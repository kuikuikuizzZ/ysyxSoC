package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class APBDelayerIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val out = new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32))
}

class apb_delayer extends BlackBox {
  val io = IO(new APBDelayerIO)
}

class APBDelayerChisel extends Module {
  val io = IO(new APBDelayerIO)

  val s_idle :: s_transmit :: s_wait :: s_resp :: Nil = Enum(4)
  val state = RegInit(s_idle)
  // B2 freq 350MHz, device typical 100MHz
  // s = 2^16, r= 3.5 , s_r = 229376
  // s = 2^16, r= 7 , s_r = 458752
  val s_r = 458752.U(32.W)
  val cnt = RegInit(0.U(32.W))
  val apb_start = io.in.penable && io.in.psel 

  switch (state) {
    is (s_idle) { when (apb_start) { state := s_transmit
                    cnt := s_r } }
    is (s_transmit) { when (io.out.pready){ state := s_wait
                        cnt := cnt >> 16
                      }.otherwise { cnt := cnt + s_r }}
    is (s_wait) { when (cnt === 1.U) { state := s_resp
                  }.otherwise { cnt := cnt - 1.U }}
    is (s_resp) { state := s_idle}
  }

  val pslverr  = RegEnable(io.out.pslverr,io.out.pready)
  val prdata   = RegEnable(io.out.prdata,io.out.pready)
  val pduser   = RegEnable(io.out.pduser,io.out.pready)

  io.out.psel    := Mux(state===s_idle || state===s_transmit, io.in.psel ,false.B)  
  io.out.penable := Mux(state===s_idle || state===s_transmit, io.in.penable ,false.B)  
  io.out.pwrite  := io.in.pwrite 
  io.out.paddr   := io.in.paddr  
  io.out.pprot   := io.in.pprot  
  io.out.pwdata  := io.in.pwdata 
  io.out.pstrb   := io.in.pstrb  
  io.out.pauser  := io.in.pauser 

  io.in.pready  := Mux(state === s_resp, true.B, false.B)
  io.in.pslverr := pslverr
  io.in.prdata  := prdata
  io.in.pduser  := pduser
}

class APBDelayerWrapper(implicit p: Parameters) extends LazyModule {
  val node = APBIdentityNode()

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val delayer = Module(new apb_delayer)
      delayer.io.clock := clock
      delayer.io.reset := reset
      delayer.io.in <> in
      out <> delayer.io.out
    }
  }
}

object APBDelayer {
  def apply()(implicit p: Parameters): APBNode = {
    val apbdelay = LazyModule(new APBDelayerWrapper)
    apbdelay.node
  }
}
