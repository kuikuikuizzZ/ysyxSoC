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

object SCKReg {
  def apply[T <: Data](_type: T, clockVal: Bool, resetVal: Bool): T = {
    val clk = (clockVal.asUInt).asBool.asClock

    withClockAndReset(clk, resetVal.asAsyncReset) {
      Reg(_type)
    }
  }
}

object NegReg {
  def apply[T <: Data](_type: T, clockVal: Bool, resetVal: Bool): T = {
    val clk = (~clockVal.asUInt).asBool.asClock

    withClockAndReset(clk, resetVal.asAsyncReset) {
      Reg(_type)
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

object RegEnableSynRet {
  def apply[T <: Data](value: T,initValue: T, cond: Bool,clockVal: Bool, resetVal: Bool): T = {
    val clk = (clockVal.asUInt).asBool.asClock

    withClockAndReset(clk, resetVal.asBool) {
      RegEnable(value,initValue,cond)
    }
  }
}