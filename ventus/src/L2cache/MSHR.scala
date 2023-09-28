/*
 * Copyright (c) 2021-2022 International Innovation Center of Tsinghua University, Shanghai
 * Ventus is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details. */

package L2cache

import chisel3._
import chisel3.util._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.tilelink._
import TLPermissions._
import TLMessages._
import MetaData._

class ScheduleRequest(params:InclusiveCacheParameters_lite) extends Bundle 
{
  val a = Decoupled(new FullRequest(params)) 
  val d = Decoupled(new DirectoryResult_lite(params))

  val dir = Decoupled(new DirectoryWrite_lite(params))

}
class  Status(params:InclusiveCacheParameters_lite)extends  DirectoryResult_lite(params)
{
  val pending =Bool()
  val pending_index= UInt(log2Ceil(params.mshrs).W)
}

class MSHR (params:InclusiveCacheParameters_lite)extends Module
{
  val io = IO(new Bundle {
    val allocate  = Flipped(Valid(new Status(params)))
    val cancel_pending = Input(Bool())
    val status    = Output( new Status(params))
    val valid     = Input(Bool())
    val mshr_wait = Input(Bool())
    val schedule  = new ScheduleRequest(params)

    val sinkd     = Flipped(Valid(new SinkDResponse(params))) 
  })

  val request = RegInit(0.U.asTypeOf(new Status(params)))

  val pending_reg=RegInit(false.B)
  val pending_index_reg=RegInit(0.U(log2Ceil(params.mshrs).W))

  io.status    := request
  io.status.pending_index:= pending_index_reg
  io.status.pending:=pending_reg

  val sche_a_valid=RegInit(false.B)  
  val sche_dir_valid=RegInit(false.B)
  val sink_d_reg=RegInit(false.B)

  when (io.allocate.valid) {
    request := io.allocate.bits 
    sink_d_reg:=false.B

  }
  when(io.allocate.valid){
    pending_reg := io.allocate.bits.pending
    pending_index_reg := io.allocate.bits.pending_index
  }.elsewhen(io.cancel_pending){
    pending_reg :=false.B
    pending_index_reg := 0.U
  }


  io.schedule.d.valid:= io.valid && sink_d_reg //为了使得最后全弹出来才拉低，需要知道这个MSHR是否还有效
  io.schedule.d.bits:=request 
  io.schedule.d.bits.hit:=false.B
  io.schedule.d.bits.dirty:=false.B
  io.schedule.d.bits.data :=Mux(sink_d_reg, RegEnable(io.sinkd.bits.data,io.sinkd.valid), request.data)


  io.schedule.a.valid:=sche_a_valid && !io.mshr_wait
  io.schedule.a.bits.set:=request.set
  io.schedule.a.bits.opcode:=Get
  io.schedule.a.bits.tag:=request.tag
  io.schedule.a.bits.l2cidx := request.l2cidx
  io.schedule.a.bits.param := request.param
  io.schedule.a.bits.put:=request.put
  io.schedule.a.bits.offset:=request.offset
  io.schedule.a.bits.source:=request.source
  io.schedule.a.bits.data:=request.data
  io.schedule.a.bits.size:=request.size
  io.schedule.a.bits.mask:= ~(0.U(params.mask_bits.W))
  when(io.schedule.a.fire()){sche_a_valid:=false.B}.elsewhen(io.allocate.valid && !io.allocate.bits.pending){
    sche_a_valid:=true.B
  }.elsewhen(pending_reg && io.cancel_pending){
    sche_a_valid:=true.B
  }.otherwise{
    sche_a_valid:=sche_a_valid
  }

  when(io.schedule.dir.fire()){sche_dir_valid:=false.B}


  io.schedule.dir.valid:=sche_dir_valid
  io.schedule.dir.bits.set:=request.set
  io.schedule.dir.bits.data.tag:=request.tag
  //io.schedule.dir.bits.data.valid:=true.B
  io.schedule.dir.bits.way:=request.way
  io.schedule.dir.bits.is_writemiss:= (request.opcode===PutFullData) ||(request.opcode===PutPartialData)




  when (io.sinkd.valid ) {
    sche_dir_valid:=true.B
    sink_d_reg :=true.B

  }


}
