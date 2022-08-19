package pipeline

import chisel3._
import chisel3.util._
class Branch_back extends Module{
  val io = IO(new Bundle{
    val out=DecoupledIO(new BranchCtrl)
    val in0=Flipped(DecoupledIO(new BranchCtrl))
    val in1=Flipped(DecoupledIO(new BranchCtrl))
  })
  val fifo0=Queue.apply(io.in0,0)
  val fifo1=Queue.apply(io.in1,0)
  val arbiter=Module(new Arbiter(new BranchCtrl(),2))
  arbiter.io.in(0)<>fifo0
  arbiter.io.in(1)<>fifo1
  arbiter.io.out<>io.out
}

class Writeback(num_x:Int,num_v:Int) extends Module{
  val io = IO(new Bundle{
    val out_v=(DecoupledIO(new WriteVecCtrl))
    val out_x=(DecoupledIO(new WriteScalarCtrl))
    val in_x=Vec(num_x,Flipped(DecoupledIO(new WriteScalarCtrl)))
    val in_v=Vec(num_v,Flipped(DecoupledIO(new WriteVecCtrl)))
  })
  //val fifo=VecInit(Seq.fill(3)(Module(new Queue((new WriteCtrl),2)).io))
  val fifo_x=for(i<-0 until num_x) yield
  { val x=Queue.apply(io.in_x(i),0)
    x
  }
  val fifo_v=for(i<-0 until num_v) yield
  { val x=Queue.apply(io.in_v(i),0)
    x
  }
  val arbiter_x=Module(new Arbiter(new WriteScalarCtrl(),num_x))
  val arbiter_v=Module(new Arbiter(new WriteVecCtrl(),num_v))
  arbiter_x.io.in<>fifo_x
  arbiter_v.io.in<>fifo_v
  arbiter_x.io.out<>io.out_x
  arbiter_v.io.out<>io.out_v
  //send to operand collector
  when(io.out_x.valid){
    //printf(p"idw=0x${Hexadecimal(io.out.bits.reg_idxw)},wxdata=0x${Hexadecimal(io.out.bits.wb_wxd_rd)},wfdata7=0x${Hexadecimal(io.out.bits.wb_wfd_rd(7))},wxd=${Hexadecimal(io.out.bits.wxd)},wfd=${Hexadecimal(io.out.bits.wfd)}\n")
  }
}