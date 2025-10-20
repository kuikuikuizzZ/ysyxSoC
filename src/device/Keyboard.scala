package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class PS2IO extends Bundle {
  val clk = Input(Bool())
  val data = Input(Bool())
}

class PS2CtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val ps2 = new PS2IO
}

class ps2_top_apb extends BlackBox {
  val io = IO(new PS2CtrlIO)
}


class ps2Chisel extends Module {
  val io = IO(new PS2CtrlIO)
  io.in  := DontCare

  val ps2Ctrl = Module(new ps2Keyboard)
  val nextdata_n = RegInit(true.B)
  val fifo = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))
  val w_ptr = RegInit(0.U(4.W))
  val ready =RegNext(ps2Ctrl.io.ready)
  val ready_rise = ps2Ctrl.io.ready & ~ready
  val is_read = io.in.psel && io.in.penable && !io.in.pwrite

  when(ready_rise) {
    fifo(w_ptr) := ps2Ctrl.io.data
    w_ptr := Mux(w_ptr <= 14.U, w_ptr + 1.U, w_ptr)
    nextdata_n := false.B
  }.otherwise{
    nextdata_n := true.B
  }
  
  when(is_read){
    for(i <- 0 until 15){
      fifo(i) := fifo(i + 1)
    }
    w_ptr := Mux(w_ptr >= 1.U, w_ptr - 1.U, w_ptr)
  }

  ps2Ctrl.io.clk := clock
  ps2Ctrl.io.reset := reset
  ps2Ctrl.io.nextdata_n :=  nextdata_n  
  ps2Ctrl.io.ps2_clk := io.ps2.clk
  ps2Ctrl.io.ps2_data := io.ps2.data

  io.in.prdata    := Mux(is_read,fifo(0),0.U)
  io.in.pready    := io.in.penable
}


class ps2Keyboard extends BlackBox with HasBlackBoxInline {
    val io = IO(new Bundle{
        val clk = Input(Clock())
        val reset = Input(Reset())
        val ps2_clk = Input(Bool())
        val ps2_data = Input(Bool())
        val nextdata_n = Input(Bool())
        val data = Output(UInt(8.W))
        val ready = Output(Bool())
        val overflow = Output(Bool())
    })
    setInline("ps2Keyboard.v",
    """module ps2Keyboard(clk,reset,ps2_clk,ps2_data,data,
      |                    ready,nextdata_n,overflow);
      |    input clk,reset,ps2_clk,ps2_data;
      |    input nextdata_n;
      |    output [7:0] data;
      |    output reg ready;
      |    output reg overflow;     // fifo overflow
      |    // internal signal, for test
      |    reg [9:0] buffer;        // ps2_data bits
      |    reg [7:0] fifo[7:0];     // data fifo
      |    reg [2:0] w_ptr,r_ptr;   // fifo write and read pointers
      |    reg [3:0] count;  // count ps2_data bits
      |    // detect falling edge of ps2_clk
      |    reg [2:0] ps2_clk_sync;
      |
      |    always @(posedge clk) begin
      |        ps2_clk_sync <=  {ps2_clk_sync[1:0],ps2_clk};
      |    end
      |
      |    wire sampling = ps2_clk_sync[2] & ~ps2_clk_sync[1];
      |
      |    always @(posedge clk) begin
      |        if (reset) begin // reset
      |            count <= 0; w_ptr <= 0; r_ptr <= 0; ready<= 0;
      |        end
      |        else begin
      |            if ( ready ) begin // read to output next data
      |                if(nextdata_n == 1'b0) //read next data
      |                begin
      |                    r_ptr <= r_ptr + 3'b1;
      |                    if(w_ptr==(r_ptr+1'b1)) //empty
      |                        ready <= 1'b0;
      |                end
      |            end
      |            if (sampling) begin
      |              if (count == 4'd10) begin
      |                if ((buffer[0] == 0) &&  // start bit
      |                    (ps2_data)       &&  // stop bit
      |                    (^buffer[9:1])) begin      // odd  parity
      |                    fifo[w_ptr] <= buffer[8:1];  // kbd scan code
      |                    w_ptr <= w_ptr+3'b1;
      |                    ready <= 1'b1;
      |                end
      |                count <= 0;     // for next
      |              end else begin
      |                buffer[count] <= ps2_data;  // store ps2_data
      |                count <= count + 3'b1;
      |              end
      |            end
      |        end
      |    end
      |    assign data = fifo[r_ptr]; //always set output data
      |endmodule  
    """.stripMargin)
 }

class APBKeyboard(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = address,
      executable    = true,
      supportsRead  = true,
      supportsWrite = true)),
    beatBytes  = 4)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val ps2_bundle = IO(new PS2IO)

    val mps2 = Module(new ps2Chisel)
    mps2.io.clock := clock
    mps2.io.reset := reset
    mps2.io.in <> in
    ps2_bundle <> mps2.io.ps2
  }
}
