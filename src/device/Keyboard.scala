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



class PS2Keyboard extends Module {
  val io = IO(new Bundle {
    val nextdata_n  = Input(Bool())       // 读请求（低有效）
    val data        = Output(UInt(8.W))
    val ready       = Output(Bool())      // FIFO数据就绪
    // val overflow    = Output(Bool())      // FIFO溢出标志
    val ps2         = new PS2IO
  })

    // 寄存器声明
    val buffer      = RegInit(0.U(10.W))    // PS/2数据缓冲
    val fifo        = Reg(Vec(8, UInt(8.W))) // 8字节FIFO
    val w_ptr       = RegInit(0.U(3.W))     // 写指针
    val r_ptr       = RegInit(0.U(3.W))     // 读指针
    val count       = RegInit(0.U(4.W))     // 位计数器
    val ready       = RegInit(false.B)      // FIFO就绪标志
    val overflow    = RegInit(false.B)      // 溢出标志
  
    // 3级同步器检测PS/2时钟下降沿[6](@ref)
    val ps2_clk_sync = RegInit(VecInit(Seq.fill(3)(true.B)))
    ps2_clk_sync(0) := io.ps2.clk
    for (i <- 1 until 3) {
      ps2_clk_sync(i) := ps2_clk_sync(i-1)
    }
    val sampling = ps2_clk_sync(2) && !ps2_clk_sync(1)  // 下降沿检测

    // FIFO读取逻辑
    when(ready && !io.nextdata_n) {
      r_ptr := r_ptr + 1.U
      when(w_ptr === (r_ptr + 1.U)) {  // FIFO变空
        ready := false.B
      }
    }

    // PS/2数据采样逻辑[7](@ref)
    when(sampling) {
      when(count === 10.U) {
        val startBit  = !buffer(0)        // 起始位应为0
        val stopBit   = io.ps2.data       // 停止位应为1
        val parity    = buffer(9,1).xorR  // 奇校验位
        
        // 帧校验通过
        when(startBit && stopBit && parity) {
          fifo(w_ptr) := buffer(8,1)     // 存储8位数据
          w_ptr := w_ptr + 1.U
          ready := true.B
          // 溢出检测：写指针赶上读指针[3](@ref)
          // overflow := overflow || (r_ptr === (w_ptr + 1.U))
        }
        count := 0.U
      }.otherwise {
        buffer := (buffer << 1) | io.ps2.data  // 移位存储数据位
        count := count + 1.U
      }
    }
  

    // 输出连接[2](@ref)
    io.data     := fifo(r_ptr)
    io.ready    := ready
    // io.overflow := overflow
}

class ps2Chisel extends Module {
  val io = IO(new PS2CtrlIO)
  io.in  := DontCare

  val ps2Ctrl = Module(new PS2Keyboard)
  val rdata = RegInit(0.U(32.W))
  val nextdata_n = RegInit(false.B)
  val ready = RegInit(false.B)
  val is_read = io.in.psel && io.in.penable && !io.in.pwrite
  when(ps2Ctrl.io.ready) {
    rdata := Cat(0.U(24.W),ps2Ctrl.io.data)
    nextdata_n := false.B
    ready := true.B
  }.elsewhen(is_read){
    nextdata_n := true.B
    ready := false.B
  }.otherwise {
    nextdata_n := true.B
    rdata := rdata
    ready := ready
  }
  ps2Ctrl.io.nextdata_n :=  nextdata_n  
  ps2Ctrl.io.ps2 <> io.ps2
  io.in.prdata    := Mux(ready,rdata,0.U)
  io.in.pready    := is_read
  io.in.pslverr   := 0.U
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
