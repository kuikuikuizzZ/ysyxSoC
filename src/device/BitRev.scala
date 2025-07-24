package ysyx

import chisel3._
import chisel3.util._

class bitrev extends BlackBox {
  val io = IO(Flipped(new SPIIO(1)))
}

class bitrevChisel(num_char_len: Int) extends RawModule { // we do not need clock and reset
  val io = IO(Flipped(new SPIIO(1)))
  val cycleCount = withReset (((io.ss(0).asBool)).asAsyncReset){ withClock(io.sck.asUInt.asBool.asClock){
     RegInit(0.U(log2Ceil(2*num_char_len+2).W))
  }}
  val data = withReset (((io.ss(0).asBool)).asAsyncReset){ withClock(io.sck.asUInt.asBool.asClock){
     RegInit(0.U(num_char_len.W))
  }}
 
  when ((!(io.ss(0).asBool)) && cycleCount <= (2*num_char_len).U && cycleCount >= num_char_len.U ) {
      io.miso := data(0).asBool
      data := Cat(0.U,data(num_char_len-1,1))
      cycleCount := cycleCount + 1.U
  } .elsewhen((!(io.ss(0).asBool)) &&  cycleCount <  num_char_len.U ) {
      io.miso := false.B
      data := Cat(data(num_char_len-2,0),io.mosi)
      cycleCount := cycleCount + 1.U
  } .otherwise {
      io.miso := true.B
  }
}
