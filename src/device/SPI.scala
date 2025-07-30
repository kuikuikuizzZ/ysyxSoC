package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import ysyx.Constants._

class SPIIO(val ssWidth: Int = 8) extends Bundle {
  val sck = Output(Bool())
  val ss = Output(UInt(ssWidth.W))
  val mosi = Output(Bool())
  val miso = Input(Bool())
}

class spi_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val spi = new SPIIO
    val spi_irq_out = Output(Bool())
  })
}

class flash extends BlackBox {
  val io = IO(Flipped(new SPIIO(1)))
}

class APBSPI(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val spi_bundle = IO(new SPIIO)
    
    val mspi = Module(new spi_top_apb)
    mspi.io.clock := clock
    mspi.io.reset := reset
    mspi.io.in    := DontCare
    // mspi.io.in <> in
    spi_bundle <> mspi.io.spi

    val counter = RegInit(0.U(2.W))
    val s_idle :: s_wait_set_ctrl_ready :: s_wait_ss_ready ::s_addr :: s_wait_ctrl_clean :: s_read :: s_read_result :: Nil = Enum(7)
    val is_flash_read = !in.pwrite && in.paddr >= FLASH_BASE && in.paddr < FLASH_BASE + FLASH_SIZE
    val state = RegInit(s_idle)

    val paddr   =   RegEnable(in.paddr, state === s_idle && is_flash_read)
    // NOTE: spi read flash reverse bits, need to reverse the data
    val rev_data = Cat(mspi.io.in.prdata(7,0),mspi.io.in.prdata(15,8), 
                        mspi.io.in.prdata(23,16), mspi.io.in.prdata(31,24))
    switch(state) {
      is(s_idle) {
          // in.penable and in.psel which is better?
          when(is_flash_read && in.psel && counter === 0.U) {
              mspi.io.in.psel     :=  true.B
              in.prdata := mspi.io.in.prdata
              in.pready := false.B
              in.pduser := mspi.io.in.pduser 
              in.pslverr := mspi.io.in.pslverr
              counter := counter + 1.U
            
          } .elsewhen(is_flash_read && in.psel && counter === 1.U) {
              mspi.io.in.penable  :=  true.B
              mspi.io.in.psel     :=  true.B

              mspi.io.in.pwrite   :=  true.B
              mspi.io.in.paddr    :=  SPI_BASE + SPI_CTRL
              mspi.io.in.pwdata   :=   0x2040.U
              mspi.io.in.pprot    :=  in.pprot
              mspi.io.in.pstrb    :=  0xf.U
              mspi.io.in.pauser   :=  in.pauser
              in.pready := false.B
          } .otherwise {
            mspi.io.in <> in
          }
          when (is_flash_read && mspi.io.in.pready ){
            counter := counter + 1.U
          }
          when (counter === 2.U) {
            counter := 0.U
            state := s_wait_set_ctrl_ready
            mspi.io.in.penable  :=  false.B
            mspi.io.in.psel     :=  false.B
            mspi.io.in.pwrite   :=  false.B
          }
      }
      is(s_wait_set_ctrl_ready) {
          in.prdata := mspi.io.in.prdata
          in.pready := false.B
          in.pduser := mspi.io.in.pduser  
          in.pslverr := mspi.io.in.pslverr 

          when (counter === 0.U){
            mspi.io.in.psel     :=  true.B
            mspi.io.in.penable  :=  false.B
            counter := counter + 1.U
          } .otherwise{
            mspi.io.in.penable  :=  true.B
            mspi.io.in.psel     :=  true.B

            mspi.io.in.pwrite   :=  true.B
            mspi.io.in.paddr    :=  SPI_BASE + SPI_SS
            mspi.io.in.pwdata   :=  SPI_SS_FLASH
            mspi.io.in.pprot    :=  in.pprot
            mspi.io.in.pstrb    :=  0xf.U
            mspi.io.in.pauser   :=  in.pauser
          }
          when(mspi.io.in.pready ){
            counter := counter + 1.U
          }
          when (counter === 2.U) {
            counter := 0.U
            state := s_wait_ss_ready
            mspi.io.in.penable  :=  false.B
            mspi.io.in.psel     :=  false.B
            mspi.io.in.pwrite   :=  false.B
          }
      }
      is(s_wait_ss_ready) {
          in.prdata := mspi.io.in.prdata
          in.pready := false.B
          in.pduser := mspi.io.in.pduser  
          in.pslverr := mspi.io.in.pslverr 

          when (counter === 0.U){
            mspi.io.in.psel     :=  true.B
            mspi.io.in.penable  :=  false.B
            counter := counter + 1.U
          } .otherwise{
            mspi.io.in.penable  :=  true.B
            mspi.io.in.psel     :=  true.B
            mspi.io.in.pwrite   :=  true.B
            mspi.io.in.paddr    :=  SPI_BASE + SPI_TX1
            mspi.io.in.pwdata   :=  Cat(0x03.U(8.W),paddr(23,0))
            mspi.io.in.pprot    :=  in.pprot
            mspi.io.in.pstrb    :=  0xf.U
            mspi.io.in.pauser   :=  in.pauser
          }

          when(mspi.io.in.pready ){
            counter := counter + 1.U
          }
          when (counter === 2.U) {
            counter := 0.U
            state := s_addr
            mspi.io.in.penable  :=  false.B
            mspi.io.in.psel     :=  false.B
            mspi.io.in.pwrite   :=  false.B
          }
      }
      is(s_addr) {
          in.prdata := mspi.io.in.prdata
          in.pready := false.B
          in.pduser := mspi.io.in.pduser 
          in.pslverr := mspi.io.in.pslverr 
          when (counter === 0.U){
            mspi.io.in.psel     :=  true.B
            mspi.io.in.penable  :=  false.B
            counter := counter + 1.U
          } .otherwise{
            mspi.io.in.penable  :=  true.B
            mspi.io.in.psel     :=  true.B
            
            mspi.io.in.pwrite   :=  true.B
            mspi.io.in.paddr    :=  SPI_BASE + SPI_CTRL
            mspi.io.in.pwdata   :=  0x100.U
            mspi.io.in.pprot    :=  in.pprot
            mspi.io.in.pstrb    :=  0xf.U
            mspi.io.in.pauser   :=  in.pauser
          }
          
          when(mspi.io.in.pready ){
            counter := counter + 1.U
          }
          when (counter === 2.U) {
            mspi.io.in.psel     :=  false.B
            mspi.io.in.penable  :=  false.B
            mspi.io.in.pwrite   :=  false.B
            counter := 0.U
            state := s_wait_ctrl_clean
          }
      }
      is(s_wait_ctrl_clean) {
          in.pready := false.B
          in.pduser := mspi.io.in.pduser 
          in.pslverr := mspi.io.in.pslverr 
          when (counter === 0.U){
            mspi.io.in.psel     :=  true.B
            mspi.io.in.penable  :=  false.B
            counter := counter + 1.U
          } .otherwise{
            mspi.io.in.penable  :=  true.B
            mspi.io.in.psel     :=  true.B
            
            mspi.io.in.pwrite   :=  false.B
            mspi.io.in.paddr    :=  SPI_BASE + SPI_CTRL
            // mspi.io.in.pwdata   :=  0x100.U
            mspi.io.in.pprot    :=  in.pprot
            mspi.io.in.pstrb    :=  0xf.U
            mspi.io.in.pauser   :=  in.pauser
          }
          
          when(mspi.io.in.pready && ((mspi.io.in.prdata & 0x100.U) === 0.U) ) {
            counter := counter + 1.U
          }
          when (counter === 2.U) {
            mspi.io.in.penable  :=  false.B
            mspi.io.in.psel     :=  false.B
            counter := 0.U
            state := s_read
          }
      }
      is(s_read) {
          in.prdata := rev_data
          in.pready := false.B
          in.pduser := mspi.io.in.pduser 
          in.pslverr := mspi.io.in.pslverr 
          when (counter === 0.U){
            mspi.io.in.psel     :=  true.B
            counter := counter + 1.U
          } .otherwise{
            mspi.io.in.penable  :=  true.B
            mspi.io.in.psel     :=  true.B
            mspi.io.in.pwrite   :=  false.B
            mspi.io.in.paddr    :=  SPI_BASE + SPI_RX0
            // mspi.io.in.pwdata   :=  0x100.U

            mspi.io.in.pprot    :=  in.pprot
            mspi.io.in.pstrb    :=  in.pstrb
            mspi.io.in.pauser   :=  in.pauser
          }
          
          when(mspi.io.in.pready ) {
            counter := counter + 1.U
            in.prdata := rev_data
          }
          when (counter === 2.U) {
            mspi.io.in.psel     :=  false.B
            mspi.io.in.penable     :=  false.B
            in.pready := true.B
            counter := 0.U
            in.prdata := rev_data
            in.pduser := mspi.io.in.pduser 
            in.pslverr := mspi.io.in.pslverr 
            state := s_idle
          }
      }

    }
     
  } 
}