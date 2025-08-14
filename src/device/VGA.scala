package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class VGAIO extends Bundle {
  val r = Output(UInt(8.W))
  val g = Output(UInt(8.W))
  val b = Output(UInt(8.W))
  val hsync = Output(Bool())
  val vsync = Output(Bool())
  val valid = Output(Bool())
}

class VGACtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val vga = new VGAIO
}

class vga_top_apb extends BlackBox {
  val io = IO(new VGACtrlIO)
}

class vgaChisel extends Module {
  val io = IO(new VGACtrlIO)

    // 可配置时序参数（640x480@60Hz）
    case class VGAParams(
        hFrontPorch: Int = 96,
        hActive:     Int = 144,
        hBackPorch:  Int = 784,
        hTotal:      Int = 800,
        vFrontPorch: Int = 2,
        vActive:     Int = 35,
        vBackPorch:  Int = 515,
        vTotal:      Int = 525
    )
    io.in.pready := true.B
    io.in.pslverr := false.B
    io.in.prdata := 0.U
    val params = VGAParams()
    
    val sram = SyncReadMem(524288, UInt(24.W))
    val xCnt = RegInit(1.U(10.W)).suggestName("xCnt")
    val yCnt = RegInit(1.U(10.W)).suggestName("yCnt")

    val is_write = io.in.psel && io.in.penable && io.in.pwrite
    when(is_write){
      sram.write(io.in.paddr(18,0), io.in.pwdata(23,0))
    }

    // 水平计数器逻辑
    when(io.reset) {
        xCnt := 1.U
    }.otherwise {
        xCnt := Mux(xCnt === params.hTotal.U, 1.U, xCnt + 1.U)
    }

    // 垂直计数器逻辑
    when(io.reset) {
        yCnt := 1.U
    }.elsewhen(xCnt === params.hTotal.U) {
        yCnt := Mux(yCnt === params.vTotal.U, 1.U, yCnt + 1.U)
    }

    // 有效区域判断
    val hValid = (xCnt > params.hActive.U) && (xCnt <= params.hBackPorch.U)
    val vValid = (yCnt > params.vActive.U) && (yCnt <= params.vBackPorch.U)
    
    // 同步信号生成（低有效脉冲）
    io.vga.vsync := yCnt > params.vFrontPorch.U
    io.vga.hsync := xCnt > params.hFrontPorch.U
    io.vga.valid := hValid && vValid

    // 像素坐标计算（消隐区归零）
    val h_addr = Mux(hValid, xCnt - (params.hActive + 1).U, 0.U)
    val v_addr = Mux(vValid, yCnt - (params.vActive + 1).U, 0.U)
    val vga_data = sram((v_addr<< 9) + h_addr)
    
    // RGB输出（直接映射高位）
    io.vga.r := vga_data(23, 16)
    io.vga.g := vga_data(15, 8)
    io.vga.b := vga_data(7, 0)
}

class APBVGA(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val vga_bundle = IO(new VGAIO)

    val mvga = Module(new vgaChisel)
    mvga.io.clock := clock
    mvga.io.reset := reset
    mvga.io.in <> in
    vga_bundle <> mvga.io.vga
  }
}
