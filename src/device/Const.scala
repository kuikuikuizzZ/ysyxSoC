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

  // GPIO
  val GPIO_BASE      =     0x10002000.asUInt(32.W)
  val GPIO_LED       =     GPIO_BASE+0x00.asUInt(32.W)
  val GPIO_SWITCH    =     GPIO_BASE+0x04.asUInt(32.W)
  val GPIO_SEG       =     GPIO_BASE+0x08.asUInt(32.W)
}

trait SPICmdConst {
  val SPI_CMD_X           =     0x00.asUInt(8.W)   // write command
  val SPI_CMD_READ        =     0x03.asUInt(8.W)   // read command
  val SPI_CMD_QUAD_READ   =     0xeb.asUInt(8.W)   // quad read command
  val SPI_CMD_QUAD_WRITE  =     0x38.asUInt(8.W)
  val SPI_CMD_QPI_ENTER   =     0x35.asUInt(8.W)
  val SPI_CMD_QPI_EXIT    =     0xf5.asUInt(8.W)
  val SPI_CMD_SET_BURST   =     0xc0.asUInt(8.W)
}

trait SdramCmdConst { 
  val SDRAM_CMD_W             = "b1111".U;
  val SDRAM_CMD_NOP           = "b0111".U;
  val SDRAM_CMD_ACTIVE        = "b0011".U;
  val SDRAM_CMD_READ          = "b0101".U;
  val SDRAM_CMD_WRITE         = "b0100".U;
  val SDRAM_CMD_TERMINATE     = "b0110".U;
  val SDRAM_CMD_PRECHARGE     = "b0010".U;
  val SDRAM_CMD_REFRESH       = "b0001".U;
  val SDRAM_CMD_LOAD_MODE     = "b0000".U;
}


object Constants extends
   AddrConst with 
    SPICmdConst with
    SdramCmdConst
{
}