package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import ysyx.Constants._
class GPIOIO extends Bundle {
  val out = Output(UInt(16.W))
  val in = Input(UInt(16.W))
  val seg = Output(Vec(8, UInt(8.W)))
}

class GPIOCtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val gpio = new GPIOIO
}

class gpio_top_apb extends BlackBox {
  val io = IO(new GPIOCtrlIO)
}


class Bcd16seg extends Module {
    val io = IO(new Bundle {
        val code = Input(UInt(4.W))
        val seg = Output(UInt(8.W))
    })
    val result = Wire(UInt(8.W))
    result := 0.U
    switch (io.code) {
        is (0.U)    { result := "b11111101".U};
        is (1.U)    { result := "b01100000".U};
        is (2.U)    { result := "b11011010".U};
        is (3.U)    { result := "b11110010".U};
        is (4.U)    { result := "b01100110".U};
        is (5.U)    { result := "b10110110".U};
        is (6.U)    { result := "b10111110".U};
        is (7.U)    { result := "b11100000".U};
        is (8.U)    { result := "b11111110".U};
        is (9.U)    { result := "b11110110".U};
        is ("ha".U) { result := "b11101110".U};
        is ("hb".U) { result := "b00111110".U};
        is ("hc".U) { result := "b10011100".U};
        is ("hd".U) { result := "b01111010".U};
        is ("he".U) { result := "b10011110".U};
        is ("hf".U) { result := "b10001110".U};
    }
    io.seg := ~result
}

class gpioChisel extends Module {
  val io = IO(new GPIOCtrlIO)
  val write_valid   =   io.in.pwrite && io.in.psel && io.in.penable
  val read_valid    =   !io.in.pwrite && io.in.psel && io.in.penable
  val align_data    =   io.in.pwdata << (io.in.paddr(1,0) << 3)
  val addr          =   Cat(io.in.paddr(31,2),0.U(2.W)) 
  val mask          =   io.in.pstrb << io.in.paddr(1,0)
  val led_reg       =   RegInit(0.U(16.W))
  val switch_reg    =   RegInit(0.U(16.W))
  val seg_reg       =   RegInit(0.U(32.W))
  val seg_decoders  =   VecInit(Seq.fill(8)(Module(new Bcd16seg).io))



  // 生成字节级掩码（每8位对应1个掩码位）
  val byteMasks = Wire(Vec(4, UInt(8.W)))
  val fullMask = byteMasks.asUInt  // 合并为完整数据宽度的掩码
  for (i <- 0 until 4) {
    byteMasks(i) := Mux(mask(i), 0xFF.U, 0x00.U)  // 使能时全掩码，否则清零[2](@ref)
  }
  io.gpio.out := led_reg
  io.in.pready := io.in.psel && io.in.penable
  io.in.pslverr := false.B
  io.in.prdata := Mux(addr === GPIO_SWITCH,Cat(0.U(16.W),
                  (switch_reg  << (io.in.paddr(1,0) << 3))),0.U)

  switch_reg := io.gpio.in
  for (i <- 0 until 8) {
    io.gpio.seg(i) := seg_decoders(i).seg
  }
  for (i <- 0 until 8) {
    seg_decoders(i).code := seg_reg(i*4+3,i*4)
  }

  when(write_valid && addr === GPIO_LED){
    led_reg := (led_reg & ~fullMask(15,0)) | (align_data & fullMask(15,0)) 
  }.otherwise{
    led_reg := led_reg
  }

  when(write_valid && addr === GPIO_SEG){
    seg_reg :=  (seg_reg & ~fullMask) | (align_data & fullMask) 
  }.otherwise{
    seg_reg := seg_reg
  }



}

class APBGPIO(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val gpio_bundle = IO(new GPIOIO)

    val mgpio = Module(new gpioChisel)
    mgpio.io.clock := clock
    mgpio.io.reset := reset
    mgpio.io.in <> in
    gpio_bundle <> mgpio.io.gpio
  }
}
