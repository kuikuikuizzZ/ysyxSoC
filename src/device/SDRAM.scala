package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import ysyx.Constants._

class SDRAMIO extends Bundle {
  val clk = Output(Bool())
  val cke = Output(Bool())
  val cs  = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we  = Output(Bool())
  val a   = Output(UInt(13.W))
  val ba  = Output(UInt(2.W))
  val dqm = Output(UInt(2.W))
  val b   = Output(Bool())
  val dq  = Analog(16.W)
}

class SDRAMPadIO extends Bundle {
  val clk = Output(Bool())
  val cke = Output(Bool())
  val cs  = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we  = Output(Bool())
  val a   = Output(UInt(13.W))
  val ba  = Output(UInt(2.W))
  val dqm = Output(UInt(4.W))
  val dq = Vec(2,Analog(16.W))
}

class sdramPmemIO extends Bundle {
  val clock       = Input(Bool())
  val reset       = Input(Bool())
  val row         = Input(UInt(13.W))
  val col         = Input(UInt(9.W))
  val ba          = Input(UInt(2.W))
  val b           = Input(Bool())
  val wen         = Input(Bool())
  val ren         = Input(Bool())
  val dqm         = Input(UInt(2.W))
  val wdata       = Input(UInt(16.W))
  val rdata       = Output(UInt(16.W))
}


class sdram_top_axi extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
    val sdram = new SDRAMPadIO
  })
}

class sdram_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val sdram = new SDRAMPadIO
  })
}

class sdramPmem extends BlackBox with HasBlackBoxInline { 
  val io = IO(new sdramPmemIO) 

    setInline("sdramPmem.v",
    """module sdramPmem(
      |  input                clock,
      |  input                reset,
      |  input                wen,
      |  input                ren,
      |  input                b,
      |  input       [12:0]   row,
      |  input       [8:0]    col,
      |  input       [1:0]    ba,
      |  input       [1:0]    dqm,
      |  input       [15:0]   wdata,
      |  output reg  [15:0]   rdata
      |);
      |import "DPI-C" function void sdram_read (input int bit_ext, input int row, input int col, input int ba, input int dqm , output int data);
      |import "DPI-C" function void sdram_write(input int bit_ext, input int row, input int col, input int ba, input int dqm , input int data);
      |  wire [31:0] row_ext ;
      |  wire [31:0] col_ext; 
      |  wire [31:0] ba_ext; 
      |  wire [31:0] dqm_ext;
      |  wire [31:0] wdata_ext; 
      |  wire [31:0] bit_ext;
      |  reg [31:0] rdata_ext;
      |  
      |  assign bit_ext   = {31'd0,b};
      |  assign row_ext   = {19'd0,row};
      |  assign col_ext   = {23'd0,col};
      |  assign ba_ext    = {30'd0,ba};
      |  assign dqm_ext   = {30'd0,dqm};
      |  assign wdata_ext = {16'd0,wdata};
      |  assign rdata     = rdata_ext[15:0];
      |  always @(*) begin
      |    if (ren ) 
      |      sdram_read(bit_ext,row_ext,col_ext,ba_ext,dqm_ext, rdata_ext);
      |    else
      |      rdata_ext = 32'd0;
      |  end
      |  // always @(posedge clock) begin
      |  always @(negedge clock) begin
      |   if (wen)
      |      sdram_write(bit_ext,row_ext,col_ext,ba_ext,dqm_ext, wdata_ext);
      |  end
      |endmodule
    """.stripMargin)
}

class sdram extends BlackBox {
  val io = IO(Flipped(new SDRAMIO))
}

class sdramPad extends RawModule{
  val io = IO(Flipped(new SDRAMPadIO))
  
  val sdram0 = Module(new sdramChisel)
  val sdram1 = Module(new sdramChisel)

  sdram0.io.clk  := io.clk  
  sdram0.io.cke  := io.cke  
  sdram0.io.cs   := io.cs    
  sdram0.io.ras  := io.ras  
  sdram0.io.cas  := io.cas  
  sdram0.io.we   := io.we    
  sdram0.io.a    := io.a    
  sdram0.io.ba   := io.ba    
  sdram0.io.b    := 0.U 
  sdram0.io.dqm  := io.dqm(1,0)  
  sdram0.io.dq   <> io.dq(0)

  sdram1.io.clk  := io.clk  
  sdram1.io.cke  := io.cke  
  sdram1.io.cs   := io.cs    
  sdram1.io.ras  := io.ras  
  sdram1.io.cas  := io.cas  
  sdram1.io.we   := io.we    
  sdram1.io.a    := io.a    
  sdram1.io.ba   := io.ba   
  sdram1.io.b   := 1.U 
  sdram1.io.dqm  := io.dqm(3,2) 
  sdram1.io.dq   <> io.dq(1)
}

class sdramChisel (read_max_length:Int = 4) extends RawModule {
  val io = IO(Flipped(new SDRAMIO))
  val dout = Wire(UInt(16.W))
  val reset = !io.cke 
  val s_idle :: s_active :: s_read :: s_write ::  Nil = Enum(4)
  val dout_en = Wire(Bool())
  val din = TriStateInBuf(io.dq, dout, dout_en) // change this if you need

  val pmem = Module(new sdramPmem)
  pmem.io.clock := io.clk
  pmem.io.reset := reset.asBool
  
  val cmd = Cat(io.cs,io.ras,io.cas,io.we)
  val CAS_latency     =     NegRegInit(0.U(2.W),io.clk,reset)
  val burst_length    =     NegRegInit(0.U(3.W),io.clk,reset)
  val row             =     NegRegInit(0.U(13.W),io.clk,reset) // row address
  val state           =     NegRegInit(s_idle,io.clk,reset)
  val last_cmd        =     NegRegInit(0.U(4.W),io.clk,reset)
  val bl_cnt          =     NegRegInit(0.U(3.W),io.clk,reset)
  val col_reg         =     NegRegInit(0.U(9.W),io.clk,reset)
  // val col             =     Wire(0.U(9.W))
  // val ba              =     Wire(0.U(2.W)) // bank address
  // val dqm             =     Wire(3.U(2.W))
  // val read_cmd        =     Wire(0.U(4.W))
  // val dq              =     SCKRegInit(0.U(16.W),io.clk,reset)

  val read_col_vec    =     NegReg(Vec(read_max_length,UInt(9.W)),io.clk,reset)
  val read_ba_vec     =     NegReg(Vec(read_max_length,UInt(2.W)),io.clk,reset)
  val read_cmd_vec    =     NegReg(Vec(read_max_length,UInt(4.W)),io.clk,reset)
  val read_dqm_vec    =     NegReg(Vec(read_max_length,UInt(2.W)),io.clk,reset)
  val read_valid_vec  =     NegReg(Vec(read_max_length,Bool()),io.clk,reset)
  val read_bl_vec     =     NegReg(Vec(read_max_length,UInt(3.W)),io.clk,reset)
  // val read_col        =     Wire(0.U(9.W))
  val read_bl         =     Wire(UInt(3.W))
  val read_cas        =     CAS_latency - 1.U

  val is_nop        =   cmd === SDRAM_CMD_NOP
  val is_read       =   cmd === SDRAM_CMD_READ
  val is_precharge  =   cmd === SDRAM_CMD_PRECHARGE
  val is_active     =   cmd === SDRAM_CMD_ACTIVE
  val is_load_mode  =   cmd === SDRAM_CMD_LOAD_MODE
  val is_terminate  =   cmd === SDRAM_CMD_TERMINATE
  val is_write      =   cmd === SDRAM_CMD_WRITE
  val last_write    =   last_cmd === SDRAM_CMD_WRITE
  val last_read     =   last_cmd === SDRAM_CMD_READ
  val bl = MuxLookup(io.a(2,0),2.U)(Seq(
    0.U -> 1.U,
    1.U -> 2.U,
    2.U -> 4.U,
    3.U -> 8.U
  ))
 
  val ren            =  state === s_read && read_bl_vec(read_cas) =/= 0.U
  val wen            =  (is_write) || ( last_write   && bl_cnt =/= 0.U)
  val ba             =  Mux(is_read || state === s_read,read_ba_vec(read_cas),  io.ba)
  val dqm            =  Mux(is_read || state === s_read,read_dqm_vec(read_cas), io.dqm)
  val read_cmd       =  Mux(is_read || state === s_read,read_cmd_vec(read_cas), cmd)
  val w_offset       =  Mux(is_write, 0.U, burst_length - bl_cnt)     
  val r_offset       =  burst_length-read_bl_vec(read_cas)
  val offset         =  Mux(is_write || state === s_write, w_offset, r_offset)
  // when in read mode, read_col_vec is used to select the column.
  // when in write mode, col should keep io.a until bl_cnt is 0.
  val col            =  Mux(is_write, io.a(8,0),Mux(is_read || state === s_read,read_col_vec(read_cas) ,col_reg))
  val read_col       =  Mux(is_read, io.a(8,0), col_reg) 

  CAS_latency       :=  Mux(is_load_mode, io.a(5,4), CAS_latency)
  burst_length      :=  Mux(is_load_mode, bl, burst_length)
  row               :=  Mux(is_active    , io.a,      row) 
  col_reg           :=  Mux(is_write || is_read, io.a(8,0), col_reg)
  last_cmd          :=  Mux(is_nop,last_cmd,cmd)
  read_valid_vec    :=  Mux(state === s_read || is_read, VecInit(!is_nop +:read_valid_vec.init  ),  VecInit(Seq.fill(read_max_length)(false.B)))
  read_cmd_vec      :=  Mux(state === s_read || is_read, VecInit( cmd    +:read_cmd_vec.init    )   ,  VecInit(Seq.fill(read_max_length)(0.U(4.W))))
  read_dqm_vec      :=  Mux(state === s_read || is_read, VecInit(io.dqm  +:read_dqm_vec.init    ) ,  VecInit(Seq.fill(read_max_length)(0.U(2.W))))
  read_ba_vec       :=  Mux(state === s_read || is_read, VecInit(io.ba   +:read_ba_vec.init     )  ,  VecInit(Seq.fill(read_max_length)(0.U(2.W))))
  read_col_vec      :=  Mux(state === s_read || is_read, VecInit(read_col   +:read_col_vec.init)    ,  VecInit(Seq.fill(read_max_length)(0.U(9.W))))
  read_bl_vec       :=  Mux(state === s_read || is_read, VecInit(read_bl  +:read_bl_vec.init     )  ,  VecInit(Seq.fill(read_max_length)(0.U(3.W))))
  read_bl           :=  Mux(is_read, burst_length, 
                            Mux(read_bl_vec(0) === 0.U, 0.U,read_bl_vec(0)-1.U)) 
  pmem.io.b       := io.b
  pmem.io.row     := row
  pmem.io.col     := col + offset
  pmem.io.ba      := ba
  pmem.io.ren     := ren
  pmem.io.wen     := wen
  pmem.io.dqm     := Mux(is_write|| last_write, dqm,read_dqm_vec(read_cas))
  pmem.io.wdata   := Mux(is_write|| last_write, din, 0.U(16.W))
  dout            := Mux(ren,pmem.io.rdata,0xffff.U(16.W))
  dout_en         := ren

  switch (state) {
    is (s_idle) {
      when(is_active) {
        state := s_active
      }.otherwise {
        state := s_idle
      }
    }
    is (s_active) {
      when (is_precharge) {
        state := s_idle
      } .elsewhen(is_read) {
        state := s_read
      }.elsewhen (is_write){
        state := s_write
        // next state should burst length - 1
        bl_cnt := burst_length - 1.U
      }.otherwise {
        state := s_active
      }
    }
    is (s_read) {
      when (is_write) {
        state := s_write
        bl_cnt := burst_length - 1.U
      } .elsewhen(read_cmd === SDRAM_CMD_PRECHARGE ) {
        state := s_idle
      } .elsewhen(read_cmd === SDRAM_CMD_ACTIVE || read_cmd === SDRAM_CMD_TERMINATE){
        state := s_active
        bl_cnt := bl_cnt
      } .otherwise {
        state := s_read
      }
    }
    is (s_write) {
      when (is_read) {
        state := s_read
      } .elsewhen(is_precharge  ) {
        state := s_idle
        bl_cnt := bl_cnt
      } .elsewhen(is_active || is_terminate){
        state := s_active
        bl_cnt := bl_cnt
      } .elsewhen(is_write) {
        state := s_write
        bl_cnt := burst_length - 1.U
      } .otherwise {
        state := s_write
        bl_cnt := Mux(bl_cnt === 0.U, bl_cnt, bl_cnt - 1.U)
      }
    }
  }
}

class AXI4SDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val beatBytes = 4
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
        address       = address,
        executable    = true,
        supportsWrite = TransferSizes(1, beatBytes),
        supportsRead  = TransferSizes(1, beatBytes),
        interleavedId = Some(0))
    ),
    beatBytes  = beatBytes)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val sdram_bundle = IO(new SDRAMPadIO)

    val msdram = Module(new sdram_top_axi)
    msdram.io.clock := clock
    msdram.io.reset := reset.asBool
    msdram.io.in <> in
    sdram_bundle <> msdram.io.sdram
  }
}

class APBSDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val sdram_bundle = IO(new SDRAMPadIO)

    val msdram = Module(new sdram_top_apb)
    msdram.io.clock := clock
    msdram.io.reset := reset.asBool
    msdram.io.in <> in
    sdram_bundle <> msdram.io.sdram
  }
}
