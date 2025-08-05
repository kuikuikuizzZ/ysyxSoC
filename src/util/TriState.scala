package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

class TriStateInBuf(bits: Int) extends BlackBox(Map("width" -> bits)) with HasBlackBoxInline {
  val io = IO(new Bundle {
    val dio = Analog(bits.W)
    val dout = Input(UInt(bits.W))
    val out_en = Input(Bool())
    val din = Output(UInt(bits.W))
  })

  setInline("TriStateInBuf.v",
    """module TriStateInBuf #(
      |  parameter width = 1
      |)(
      |    inout  [width-1:0] dio,
      |    input  [width-1:0] dout,
      |    input              out_en,
      |    output [width-1:0] din
      |);
      |  assign din = dio;
      |  assign dio = out_en ? dout : {width{1'bz}};
      |endmodule
    """.stripMargin)
}

object TriStateInBuf {
  def apply(dio: Analog, dout: UInt, out_en: Bool) = {
    val buf = Module(new TriStateInBuf(dio.getWidth))
    buf.io.dio <> dio
    buf.io.dout := dout
    buf.io.out_en := out_en
    buf.io.din
  }
}

class AnalogSwitch extends Module {
  val io = IO(new Bundle {
    val sel      = Input(Bool())               // 选择信号
    val analogIn = Vec(2, Analog(16.W)) // 两组输入信号
    val analogOut = Analog(16.W)               // 输出信号
  })

  // 禁止未选中的输入驱动总线
  io.analogIn(0) <> DontCare
  io.analogIn(1) <> DontCare

  // 条件连接：仅选中的输入与输出连通
  when (io.sel) {
    io.analogIn(1) <> io.analogOut  // sel=true 时连接第二路
  } .otherwise {
    io.analogIn(0) <> io.analogOut  // sel=false 时连接第一路
  }
}
