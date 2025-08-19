module sdram_top_axi(
  input         clock,
  input         reset,
  output        in_awready,
  input         in_awvalid,
  input  [31:0] in_awaddr,
  input  [3:0]  in_awid,
  input  [7:0]  in_awlen,
  input  [2:0]  in_awsize,
  input  [1:0]  in_awburst,
  output        in_wready,
  input         in_wvalid,
  input  [31:0] in_wdata,
  input  [3:0]  in_wstrb,
  input         in_wlast,
  input         in_bready,
  output        in_bvalid,
  output [1:0]  in_bresp,
  output [3:0]  in_bid,
  output        in_arready,
  input         in_arvalid,
  input  [31:0] in_araddr,
  input  [3:0]  in_arid,
  input  [7:0]  in_arlen,
  input  [2:0]  in_arsize,
  input  [1:0]  in_arburst,
  input         in_rready,
  output        in_rvalid,
  output [1:0]  in_rresp,
  output [31:0] in_rdata,
  output        in_rlast,
  output [3:0]  in_rid,

  output        sdram_clk,
  output        sdram_cke,
  output        sdram_cs,
  output        sdram_ras,
  output        sdram_cas,
  output        sdram_we,
  output [12:0] sdram_a,
  output [ 2:0] sdram_ba,
  output [ 3:0] sdram_dqm,
  inout  [15:0] sdram_dq_0,
  inout  [15:0] sdram_dq_1,
  inout  [15:0] sdram_dq_2,
  inout  [15:0] sdram_dq_3
);
//-----------------------------------------------------------------
// Defines / Local params
//-----------------------------------------------------------------
localparam CMD_W             = 4;
localparam CMD_NOP           = 4'b0111;
localparam CMD_ACTIVE        = 4'b0011;
localparam CMD_READ          = 4'b0101;
localparam CMD_WRITE         = 4'b0100;
localparam CMD_TERMINATE     = 4'b0110;
localparam CMD_PRECHARGE     = 4'b0010;
localparam CMD_REFRESH       = 4'b0001;
localparam CMD_LOAD_MODE     = 4'b0000;

//-----------------------------------------------------------------
// Registers / Wires
//-----------------------------------------------------------------
  wire sdram_dout_en;
  wire [31:0] sdram_dout;
  wire [31:0] sdram_dq;
  wire [3:0]  cmd;
  reg last_ba2;
  // word extend implements on ba, original ba[1:0],
  // ba[2] is word extend bit.
  wire ba2 ;
  
  always @(posedge clock) begin
    if (reset) begin
      last_ba2 <= 1'b1;
    end
    if (cmd == CMD_WRITE || cmd == CMD_READ || cmd == CMD_ACTIVE)
      last_ba2 <= sdram_ba[2];
    else 
      last_ba2 <= last_ba2;
    end
  
  assign cmd = {sdram_cs,sdram_ras, sdram_cas, sdram_we};
  assign ba2 = (cmd == CMD_WRITE || cmd == CMD_READ || cmd == CMD_ACTIVE) ?  sdram_ba[2] : last_ba2;
  assign sdram_dq_0 = sdram_dout_en ? (!ba2 ? sdram_dout[15:0]  :16'bz) : 16'bz;
  assign sdram_dq_1 = sdram_dout_en ? (!ba2 ? sdram_dout[31:16] :16'bz) : 16'bz;
  assign sdram_dq_2 = sdram_dout_en ? ( ba2 ? sdram_dout[15:0]  :16'bz) : 16'bz;
  assign sdram_dq_3 = sdram_dout_en ? ( ba2 ? sdram_dout[31:16] :16'bz) : 16'bz;
  assign sdram_dq   = ba2 ? {sdram_dq_3,sdram_dq_2} : {sdram_dq_1,sdram_dq_0}  ;

  sdram_axi #(
    .SDRAM_MHZ(100),
    .SDRAM_ADDR_W(25),
    .SDRAM_COL_W(9),
    .SDRAM_READ_LATENCY(0)
  ) u_sdram_axi(
    .clk_i(clock),
    .rst_i(reset),
    .inport_awvalid_i(in_awvalid),
    .inport_awaddr_i(in_awaddr),
    .inport_awid_i(in_awid),
    .inport_awlen_i(in_awlen),
    .inport_awburst_i(in_awburst),
    .inport_wvalid_i(in_wvalid),
    .inport_wdata_i(in_wdata),
    .inport_wstrb_i(in_wstrb),
    .inport_wlast_i(in_wlast),
    .inport_bready_i(in_bready),
    .inport_arvalid_i(in_arvalid),
    .inport_araddr_i(in_araddr),
    .inport_arid_i(in_arid),
    .inport_arlen_i(in_arlen),
    .inport_arburst_i(in_arburst),
    .inport_rready_i(in_rready),

    .inport_awready_o(in_awready),
    .inport_wready_o(in_wready),
    .inport_bvalid_o(in_bvalid),
    .inport_bresp_o(in_bresp),
    .inport_bid_o(in_bid),
    .inport_arready_o(in_arready),
    .inport_rvalid_o(in_rvalid),
    .inport_rdata_o(in_rdata),
    .inport_rresp_o(in_rresp),
    .inport_rid_o(in_rid),
    .inport_rlast_o(in_rlast),
    .sdram_clk_o(sdram_clk),
    .sdram_cke_o(sdram_cke),
    .sdram_cs_o(sdram_cs),
    .sdram_ras_o(sdram_ras),
    .sdram_cas_o(sdram_cas),
    .sdram_we_o(sdram_we),
    .sdram_dqm_o(sdram_dqm),
    .sdram_addr_o(sdram_a),
    .sdram_ba_o(sdram_ba),
    .sdram_data_input_i(sdram_dq),
    .sdram_data_output_o(sdram_dout),
    .sdram_data_out_en_o(sdram_dout_en)
  );

endmodule
