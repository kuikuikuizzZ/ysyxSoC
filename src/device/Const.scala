package ysyx
import chisel3._
import chisel3.util._

trait AddrConst {
  val SRAM_BASE     =     0x0f000000.asUInt(32.W)
  val SRAM_SIZE     =     0x2000.asUInt(32.W)        // 8KB
  val MROM_BASE     =     0x20000000.asUInt(32.W)
  val MROM_SIZE     =     0x1000.asUInt(32.W)        // 4KB
  val FLASH_BASE    =     0x30000000.asUInt(32.W)
  val FLASH_SIZE    =     0x10000000.asUInt(32.W)
  val SPI_BASE      =     0x10001000.asUInt(32.W)
  val SPI_SIZE      =     0x1000.asUInt(32.W)     // 4KB
  val SPI_CTRL      =     0x10.asUInt(32.W)        
  val SPI_TX1       =     0x04.asUInt(32.W)
  val SPI_RX0       =     0x00.asUInt(32.W)
  val SPI_TX0       =     0x00.asUInt(32.W)
  val SPI_SS        =     0x18.asUInt(32.W)   
  val SPI_SS_FLASH  =     0x01.asUInt(8.W)     
  // val PSRAM_BASE    =     0x80000000.asUInt(32.W)
  // val PSRAM_SIZE    =     0x20000000.asUInt(32.W)
}

trait CmdConst {
  val SPI_CMD_X           =     0x00.asUInt(8.W)   // write command
  val SPI_CMD_READ        =     0x03.asUInt(8.W)   // read command
  val SPI_CMD_QUAD_READ   =     0xeb.asUInt(8.W)   // quad read command
  val SPI_CMD_QUAD_WRITE  =     0x38.asUInt(8.W)
  val SPI_CMD_QPI_ENTER   =     0x35.asUInt(8.W)
  val SPI_CMD_QPI_EXIT    =     0xf5.asUInt(8.W)
  val SPI_CMD_SET_BURST   =     0xc0.asUInt(8.W)
}

object Constants extends
   AddrConst with 
    CmdConst 
{
}