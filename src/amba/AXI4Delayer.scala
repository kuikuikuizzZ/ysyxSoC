package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class AXI4DelayerIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
  val out = new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4))
}

class axi4_delayer extends BlackBox {
  val io = IO(new AXI4DelayerIO)
}

class AXI4DelayerChisel extends Module {
  val io = IO(new AXI4DelayerIO)


  val s_idle :: s_transmit :: s_wait :: s_resp :: Nil = Enum(4)
  val state = RegInit(s_idle)
  // B2 freq 350 MHz, device typical 100MHz
  // B4 freq 550 MHz, device typical 100MHz  
  // s = 2^16, r= 3.5 , s_r = 229376
  val s_r = 229376.U(32.W)
  val cnt = RegInit(0.U(32.W))
  val axi_start = (io.in.ar.valid && io.in.ar.ready) || (io.in.aw.valid && io.in.aw.ready)
  val axi_finsh = (io.out.r.valid &&  io.out.r.ready) || (io.out.b.valid && io.out.b.ready)
  val is_read = RegEnable(io.in.ar.valid,io.in.ar.valid || state === s_idle)
  val is_write = RegEnable(io.in.aw.valid,io.in.aw.valid || state === s_idle)

  val rvalid  =  RegEnable(io.out.r.valid ,io.out.r.valid || state === s_idle )
  val rdata   =  RegEnable(io.out.r.bits.data  ,(io.out.r.valid &&  io.out.r.ready) && state === s_transmit)
  val rresp   =  RegEnable(io.out.r.bits.resp  ,(io.out.r.valid &&  io.out.r.ready) && state === s_transmit)
  val rid     =  RegEnable(io.out.r.bits.id    ,(io.out.r.valid &&  io.out.r.ready) && state === s_transmit)
  val rlast   =  RegEnable(io.out.r.bits.last  ,(io.out.r.valid &&  io.out.r.ready) && state === s_transmit)

  // val wdata   =  Mux(io.in.w.valid,io.in.w.bits.data,RegEnable(io.in.w.bits.data ,io.in.w.valid && io.in.w.ready ))
  // val wstrb   =  Mux(io.in.w.valid,io.in.w.bits.strb,RegEnable(io.in.w.bits.strb ,io.in.w.valid && io.in.w.ready ))
  // val wlast   =  Mux(io.in.w.valid,io.in.w.bits.last,RegEnable(io.in.w.bits.last ,io.in.w.valid && io.in.w.ready ))
  
  val bvalid  = RegEnable(io.out.b.valid ,io.out.b.valid || state === s_idle)
  val bresp   = RegEnable(io.out.b.bits.resp  ,(io.out.b.valid &&  io.out.b.ready)&& state === s_transmit)
  val bid     = RegEnable(io.out.b.bits.id    ,(io.out.b.valid &&  io.out.b.ready)&& state === s_transmit)


  switch (state) {
    is (s_idle) { when (axi_start) { state := s_transmit
                    cnt := s_r } }
    is (s_transmit) { when (axi_finsh){ 
                        state := s_wait
                        cnt := cnt >> 16
                      }.otherwise { cnt := cnt + s_r }}
    is (s_wait) { when (cnt === 1.U) { state := s_resp
                  }.otherwise { cnt := cnt - 1.U }}
    is (s_resp) {
      when(is_read && rvalid && !rlast ){
        state := s_transmit
        cnt := s_r
      } .otherwise {
        state := s_idle
      }
    }
  }

  io.out := DontCare
  io.out.ar.valid   := Mux(state===s_idle ,io.in.ar.valid,false.B)
  io.out.ar.bits.addr    := io.in.ar.bits.addr 
  io.out.ar.bits.id      := io.in.ar.bits.id   
  io.out.ar.bits.len     := io.in.ar.bits.len  
  io.out.ar.bits.size    := io.in.ar.bits.size 
  io.out.ar.bits.burst   := io.in.ar.bits.burst
  io.in.ar.ready    := io.out.ar.ready

  io.out.aw.valid   := Mux(state===s_idle ,io.in.aw.valid,false.B)
  io.out.aw.bits.addr    := io.in.aw.bits.addr  
  io.out.aw.bits.id      := io.in.aw.bits.id    
  io.out.aw.bits.len     := io.in.aw.bits.len   
  io.out.aw.bits.size    := io.in.aw.bits.size  
  io.out.aw.bits.burst   := io.in.aw.bits.burst 
  io.in.aw.ready    := io.out.aw.ready 

  io.out.w.valid    := Mux(state===s_idle ,io.in.w.valid,false.B)
  io.out.w.bits.data     := io.in.w.bits.data
  io.out.w.bits.strb     := io.in.w.bits.strb  
  io.out.w.bits.last     := io.in.w.bits.last  
  io.in.w.ready     := io.out.w.ready   

  io.out.b.ready    := Mux(state===s_idle || state===s_transmit,io.in.b.ready,false.B)
  io.in.b.valid     := Mux(state === s_resp, bvalid, false.B)  
  io.in.b.bits.resp      := bresp   
  io.in.b.bits.id        := bid  
  
  io.out.r.ready    := Mux(state===s_idle || state===s_transmit,io.in.r.ready,false.B)
  io.in.r.valid     := Mux(state === s_resp, rvalid, false.B)
  io.in.r.bits.data      := rdata
  io.in.r.bits.resp      := rresp   
  io.in.r.bits.id        := rid   
  io.in.r.bits.last      := rlast 
}

class AXI4DelayerWrapper(implicit p: Parameters) extends LazyModule {
  val node = AXI4IdentityNode()

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val delayer = Module(new axi4_delayer)
      delayer.io.clock := clock
      delayer.io.reset := reset
      delayer.io.in <> in
      out <> delayer.io.out
    }
  }
}

object AXI4Delayer {
  def apply()(implicit p: Parameters): AXI4Node = {
    val axi4delay = LazyModule(new AXI4DelayerWrapper)
    axi4delay.node
  }
}
