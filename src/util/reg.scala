package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

object NegRegInit {
  def apply[T <: Data](initValue: T, clockVal: Bool, resetVal: Bool): T = {
    val negClock = (~ clockVal.asUInt).asBool.asClock

    withClockAndReset(negClock, resetVal) {
      RegInit(initValue)
    }
  }
}

object SCKRegInit {
  def apply[T <: Data](initValue: T, clockVal: Bool, resetVal: Bool): T = {
    val clk = (clockVal.asUInt).asBool.asClock

    withClockAndReset(clk, resetVal.asAsyncReset) {
      RegInit(initValue)
    }
  }
}

object RegSynRetInit {
  def apply[T <: Data](initValue: T, clockVal: Bool, resetVal: Bool): T = {
    val clk = (clockVal.asUInt).asBool.asClock

    withClockAndReset(clk, resetVal.asBool) {
      RegInit(initValue)
    }
  }
}
