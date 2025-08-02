package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import ysyx.Constants._





class QSPIIO extends Bundle {
  val sck = Output(Bool())
  val ce_n = Output(Bool())
  val dio = Analog(4.W)
}

class psram_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val qspi = new QSPIIO
  })
}

class psram extends  BlackBox  {
  val io = IO(Flipped(new QSPIIO))
}

class psram_array extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val addr =  (Input(UInt(24.W)))
    val wdata = (Input(UInt(32.W)))
    val rdata = (Output(UInt(32.W)))
    val reset = (Input(Bool()))
    val clock =   (Input(Bool()))
    val length = (Input(UInt(5.W))) // length of the data to be read or written
    val wen =   (Input(Bool()))
    val ren =   (Input(Bool()))
  })

  setInline("PsramArray.v",
    """module psram_array(
      |  input               clock,
      |  input               reset,
      |  input               wen,
      |  input               ren,
      |  input       [23:0]  addr,
      |  input       [31:0]  wdata,
      |  input       [4:0]   length,
      |  output reg  [31:0]  rdata
      |);
      |import "DPI-C" function void psram_read (input int addr, input int length , output int data);
      |import "DPI-C" function void psram_write(input int addr, input int length , input int data);
      |  reg [31:0] addr_ext ; // Extend address to 32 bits
      |  reg [31:0] length_ext; // Extend address to 32 bits
      |
      |  reg wen_reg;
      |  reg [31:0] wdata_reg;
      |  always @(negedge clock) begin
      |    if (ren ) 
      |      psram_read(addr_ext,length_ext, rdata);
      |    else
      |      rdata = 32'd0;
      |  end
      |  always @(posedge reset) begin
      |   if (wen)
      |      psram_write(addr_ext,length_ext, wdata_reg);
      |  end
      | always @(negedge clock) begin
      |   wen_reg   <= wen;
      |   wdata_reg <= wdata;
      |   addr_ext  <= {8'b0, addr};
      |   length_ext <= {27'b0, length};
      | end 
      |endmodule
    """.stripMargin)
}

class psramChisel extends RawModule {
  val io = IO(Flipped(new QSPIIO))
  val dout = Wire(UInt(4.W))
  // input dout , output din
  val reset = io.ce_n
  val dout_en = Wire(Bool())
  val din = TriStateInBuf(io.dio, dout, dout_en) // change this if you need

  val s_cmd :: s_addr :: s_wait :: s_data  :: Nil = Enum(4)
  val state               =   SCKRegInit(s_cmd,io.sck,reset)
  val counter             =   SCKRegInit(0.U(5.W),io.sck,reset) // counter for cmd, addr, data, wait cycle
  val length              = Mux(state === s_data,(counter+1.U) >> 1.U,4.U) // length of the data to be read or written, 0 means no data
  val psram_array         = Module(new psram_array)
  psram_array.io.reset   := reset
  psram_array.io.clock   := io.sck
  psram_array.io.length  := length
  // only take SI/SO[0] as cmd bit
  val cmd           =     SCKRegInit(0.U(SPI_CMD_X.getWidth.W),io.sck,reset) // command
  val addr          =     SCKRegInit(0.U(24.W),io.sck,reset)
  val din_data      =     SCKRegInit(0.U(32.W),io.sck,reset)
  val dout_data     =     SCKRegInit(0.U(32.W),io.sck,reset)
  val wen_reg       =     RegSynRetInit(false.B,io.sck,reset) // write enable register
  // is qpi only be set when cmd is SPI_CMD_QPI_ENTER or SPI_CMD_QPI_EXIT
  val is_qpi        =   RegSynRetInit(cmd === SPI_CMD_QPI_ENTER,io.sck,reset) // qpi mode)
  val cmd_cycle     =   Mux(is_qpi, 2.U, 8.U) - 1.U   // qpi cmd cycle is 2, normal cmd cycle is 8
  val addr_cycle    =   SCKRegInit(6.U(5.W),io.sck,reset) - 1.U       // TODO: support set by cmd
  val data_cycle    =   SCKRegInit(8.U(5.W),io.sck,reset) - 1.U
  val wait_cycle    =   SCKRegInit(6.U(5.W),io.sck,reset)        // wait cycle
  val ren           =   (state === s_wait) && (counter === wait_cycle) // when wait cycle is over, ren is high
  val wen           =   wen_reg && ((counter === data_cycle) || reset)  // wen is high when data cycle is over
  val is_quad_cmd = (cmd === SPI_CMD_QPI_ENTER) || (cmd === SPI_CMD_QPI_EXIT) 
  val rev_data      =  Cat(psram_array.io.rdata(7,0),psram_array.io.rdata(15,8),
                          psram_array.io.rdata(23,16),psram_array.io.rdata(31,24))
  val rev_din      =  MuxLookup(length,din_data)(Seq(
    1.U -> din_data,
    2.U -> Cat(din_data(31,16),din_data(7,0),din_data(15,8)),
    4.U -> Cat(din_data(7,0),din_data(15,8),din_data(23,16),din_data(31,24))
  ))
  
  // when read psram dout is valid, dout_en is high 
  wen_reg          := (cmd === SPI_CMD_QUAD_WRITE) && (state === s_data)
  is_qpi    :=  Mux( is_quad_cmd, cmd === SPI_CMD_QPI_ENTER, is_qpi) // exit qpi mode
  dout      :=  Mux(state === s_data, dout_data(31,28), 0xf.U) // dout is 4 bits
  dout_en   :=  (cmd === SPI_CMD_QUAD_READ) &&  (state === s_data)
  cmd       :=  Mux(state === s_cmd,Mux(is_qpi,Cat(cmd(3,0),din(3,0)),Cat(cmd(6,0),din(0))),cmd) // command
  addr      :=  Mux(state === s_addr,Cat(addr(19,0),din(3,0)),addr)
  din_data  :=  Mux(state === s_data,Cat(din_data(27,0),din(3,0)),din_data)
  dout_data :=  Mux(state === s_data ,Cat(dout_data(27,0),0.U(4.W)),Mux(ren,rev_data,dout_data)) // dout_data is 32 bits
  

  switch(state) {
    is (s_cmd)  { counter := Mux(!io.ce_n,Mux(counter < cmd_cycle, counter + 1.U,0.U),0.U)  
                  state := Mux(counter < cmd_cycle,  s_cmd, 
                            Mux(is_quad_cmd,s_cmd,s_addr)) } 
    is (s_addr) { counter := Mux(counter < addr_cycle, counter + 1.U,0.U)
                  state := Mux(counter < addr_cycle, s_addr, 
                  Mux(cmd === SPI_CMD_QUAD_READ,  s_wait, s_data)) }
    is (s_wait) { counter := Mux(counter < wait_cycle, counter + 1.U,0.U)
                  state := Mux(counter < wait_cycle, s_wait, s_data)}
    is (s_data) { counter := Mux(counter < data_cycle, counter + 1.U,0.U)
                 state := Mux(counter < data_cycle , s_data, s_cmd) }
  }

  psram_array.io.addr   :=    addr
  psram_array.io.ren    :=    ren
  psram_array.io.wen    :=    wen
  psram_array.io.wdata  :=    rev_din
}

class APBPSRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val qspi_bundle = IO(new QSPIIO)

    val mpsram = Module(new psram_top_apb)
    mpsram.io.clock := clock
    mpsram.io.reset := reset
    mpsram.io.in <> in
    qspi_bundle <> mpsram.io.qspi
  }
}
