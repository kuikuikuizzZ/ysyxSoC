/*
	Copyright 2020 Efabless Corp.

	Author: Mohamed Shalan (mshalan@efabless.com)

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at:
	http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

`timescale              1ns/1ps
`default_nettype        none

// Using EBH Command
module EF_PSRAM_CTRL_wb (
    // WB bus Interface
    input   wire        clk_i,
    input   wire        rst_i,
    input   wire [31:0] adr_i,
    input   wire [31:0] dat_i,
    output  wire [31:0] dat_o,
    input   wire [3:0]  sel_i,
    input   wire        cyc_i,
    input   wire        stb_i,
    output  wire        ack_o,
    input   wire        we_i,

    // External Interface to Quad I/O
    output  wire            sck,
    output  wire            ce_n,
    input   wire [3:0]      din,
    output  wire [3:0]      dout,
    output  wire [3:0]      douten
);

    localparam  [1:0]   ST_IDLE = 2'b00,
                        ST_WAIT = 2'b01,
                        QPI_INIT = 2'b10;
    
    wire        mr_sck;
    wire        mr_ce_n;
    wire [3:0]  mr_din;
    wire [3:0]  mr_dout;
    wire        mr_doe;

    wire        mw_sck;
    wire        mw_ce_n;
    wire [3:0]  mw_din;
    wire [3:0]  mw_dout;
    wire        mw_doe;

    wire        qpi_sck;
    wire        qpi_ce_n;
    wire [3:0]  qpi_dout;
    wire        qpi_doe;

    // PSRAM Reader and Writer wires
    wire        mr_rd;
    wire        mr_done;
    wire        mw_wr;
    wire        mw_done;
    wire        qpi_init;
    wire        qpi_init_done;
    wire        is_qpi_init;
    //wire        doe;

    // WB Control Signals
    wire        wb_valid        =   cyc_i & stb_i;
    wire        wb_we           =   we_i & wb_valid;
    wire        wb_re           =   ~we_i & wb_valid;
    //wire[3:0]   wb_byte_sel     =   sel_i & {4{wb_we}};

    // The FSM
    reg    [1:0]    state, nstate;
    reg             is_qpi;
    always @ (posedge clk_i or posedge rst_i)
        if(rst_i)
            state <= ST_IDLE;
            // state <= QPI_INIT;
        else
            state <= nstate;

    always @* begin
        case(state)
            ST_IDLE :
                if(wb_valid)
                    nstate = ST_WAIT;
                else
                    nstate = ST_IDLE;

            ST_WAIT :
                if((mw_done & wb_we) | (mr_done & wb_re))
                    nstate = ST_IDLE;
                else
                    nstate = ST_WAIT;
            QPI_INIT:
                if(qpi_init_done) begin
                    nstate = ST_IDLE;
                    is_qpi = 1'b1;
                end
                else
                    nstate = QPI_INIT;
            default: begin
                is_qpi = 1'b0;
                nstate = ST_IDLE;
            end
        endcase
    end

    wire [2:0]  size =  (sel_i == 4'b0001) ? 1 :
                        (sel_i == 4'b0010) ? 1 :
                        (sel_i == 4'b0100) ? 1 :
                        (sel_i == 4'b1000) ? 1 :
                        (sel_i == 4'b0011) ? 2 :
                        (sel_i == 4'b1100) ? 2 :
                        (sel_i == 4'b1111) ? 4 : 4;



    wire [7:0]  byte0 = (sel_i[0])          ? dat_i[7:0]   :
                        (sel_i[1] & size==1)? dat_i[15:8]  :
                        (sel_i[2] & size==1)? dat_i[23:16] :
                        (sel_i[3] & size==1)? dat_i[31:24] :
                        (sel_i[2] & size==2)? dat_i[23:16] :
                        dat_i[7:0];

    wire [7:0]  byte1 = (sel_i[1])          ? dat_i[15:8]  :
                        dat_i[31:24];

    wire [7:0]  byte2 = dat_i[23:16];

    wire [7:0]  byte3 = dat_i[31:24];

    wire [31:0] wdata = {byte3, byte2, byte1, byte0};

    /*
    wire [1:0]  waddr = (size==1 && sel_i[0]==1) ? 2'b00 :
                        (size==1 && sel_i[1]==1) ? 2'b01 :
                        (size==1 && sel_i[2]==1) ? 2'b10 :
                        (size==1 && sel_i[3]==1) ? 2'b11 :
                        (size==2 && sel_i[2]==1) ? 2'b10 :
                        2'b00;
                      */

    assign mr_rd    = ( (state==ST_IDLE ) & wb_re );
    assign mw_wr    = ( (state==ST_IDLE ) & wb_we );
    assign qpi_init = (state == QPI_INIT);

    PSRAM_READER MR (
        .clk(clk_i),
        .rst_n(~rst_i),
        .is_qpi(is_qpi),
        .addr({adr_i[23:2],2'b0}),
        .rd(mr_rd),
        //.size(size), Always read a word
        .size(3'd4),
        .done(mr_done),
        .line(dat_o),
        .sck(mr_sck),
        .ce_n(mr_ce_n),
        .din(mr_din),
        .dout(mr_dout),
        .douten(mr_doe)
    );

    PSRAM_WRITER MW (
        .clk(clk_i),
        .rst_n(~rst_i),
        .is_qpi(is_qpi),
        .addr({adr_i[23:0]}),
        .wr(mw_wr),
        .size(size),
        .done(mw_done),
        .line(wdata),
        .sck(mw_sck),
        .ce_n(mw_ce_n),
        .din(mw_din),
        .dout(mw_dout),
        .douten(mw_doe)
    );

    PSRAM_QPI_INIT qpi (
        .clk(clk_i),
        .rst_n(~rst_i),
        .qpi_init(qpi_init),
        .done(qpi_init_done),
        .sck(qpi_sck),
        .ce_n(qpi_ce_n),
        .dout(qpi_dout),
        .douten(qpi_doe)
    );
    assign is_qpi_init = (state == QPI_INIT);
    assign sck      = is_qpi_init? qpi_sck : (wb_we ? mw_sck  : mr_sck);
    assign ce_n     = is_qpi_init? qpi_ce_n : (wb_we ? mw_ce_n : mr_ce_n);
    assign dout     = is_qpi_init? qpi_dout : (wb_we ? mw_dout : mr_dout);
    assign douten   = is_qpi_init? {4{qpi_doe}} : (wb_we ? {4{mw_doe}}  : {4{mr_doe}});

    assign mw_din = din;
    assign mr_din = din;
    assign ack_o = is_qpi_init? qpi_init_done : ( wb_we ? mw_done :mr_done );

endmodule


module PSRAM_QPI_INIT (
    input   wire        clk,
    input   wire        rst_n,
    input   wire        qpi_init,
    output  reg         done,
    output  reg         sck,
    output  reg         ce_n,
    output  reg [3:0]   dout,
    output              douten
);

    localparam  IDLE = 1'b0,
                INIT = 1'b1;

    reg state, nstate;
    
    reg [3:0]   counter;

    wire[7:0]  CMD_35H = 8'h35; // Command to enter QPI mode

    always @*
        case (state)
            IDLE: if(qpi_init) nstate = INIT; else nstate = IDLE;
            INIT: if(done) nstate = IDLE; else nstate = INIT;
        endcase

    always @ (posedge clk or negedge rst_n)
        if(!rst_n) state <= IDLE;
        else state <= nstate;

    // Drive the Serial Clock (sck) @ clk/2
    always @ (posedge clk or negedge rst_n)
        if(!rst_n)
            sck <= 1'b0;
        else if(~ce_n)
            sck <= ~ sck;
        else if(state == IDLE)
            sck <= 1'b0;

    // ce_n logic
    always @ (posedge clk or negedge rst_n)
        if(!rst_n)
            ce_n <= 1'b1;
        else if(state == INIT)
            ce_n <= 1'b0;
        else
            ce_n <= 1'b1;

    always @ (posedge clk or negedge rst_n)
        if(!rst_n)
            counter <= 4'b0;
        else if(sck & ~done)
            counter <= counter + 1'b1;
        else if(state == IDLE)
            counter <= 4'b0;

    assign dout     = (counter <= 8)   ?   {3'b0, CMD_35H[7 - counter]} : 4'hf;
    assign douten   = (~ce_n);
    assign done     = (counter == 9 );

endmodule