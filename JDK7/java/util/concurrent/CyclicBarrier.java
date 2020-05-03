package jdk7.util.concurrent;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;

import jdk7.util.concurrent.locks.ReentrantLock;

public class CyclicBarrier {
	
    private static class Generation {
        boolean broken = false;
    }
    
	//同步操作锁
	private final ReentrantLock lock = new ReentrantLock();
	//线程拦截器
	private final Condition trip = lock.newCondition();
	//每次拦截的线程数
	private final int parties;
	//栅栏打翻时执行的任务
	private final Runnable barrierCommand;
	//表示栅栏的当前代
	private Generation generation = new Generation();
	//计数器
	private int count;

	//切换栅栏到下一代
	private void nextGeneration() {
	    //唤醒条件队列所有线程
	    trip.signalAll();
	    //设置计数器的值为需要拦截的线程数
	    count = parties;
	    //重新设置栅栏代次
	    generation = new Generation();
	}

	//打翻当前栅栏
	private void breakBarrier() {
		//将当前栅栏状态设置为打翻
	    generation.broken = true;
	    //设置计数器的值为需要拦截的线程数
	    count = parties;
	    //唤醒所有线程
	    trip.signalAll();
	}

	//核心等待方法
	private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
		final ReentrantLock lock = this.lock;
		lock.lock();
		try {
			final Generation g = generation;
			//检查当前栅栏是否被打翻
			if (g.broken) {
				throw new BrokenBarrierException();
			}
	        //检查当前线程是否被中断
	        if (Thread.interrupted()) {
	        	//如果当前线程被中断会做以下三件事
	        	//1.打翻当前栅栏
	        	//2.唤醒拦截的所有线程
	        	//3.抛出中断异常
	            breakBarrier();
	            throw new InterruptedException();
	        }
	       //每次都将计数器的值减1
	       int index = --count;
	       //计数器的值减为0则需唤醒所有线程并转换到下一代
	       if (index == 0) {
	    	   boolean ranAction = false;
	    	   try {
	    		   //唤醒所有线程前先执行指定的任务
	    		   final Runnable command = barrierCommand;
	    		   if (command != null) {
	    			   command.run();
	    		   }
	    		   ranAction = true;
	    		   //唤醒所有线程并转到下一代
	    		   nextGeneration();
	    		   return 0;
	    	   } finally {
	    		   //确保在任务未成功执行时能将所有线程唤醒
	    		   if (!ranAction) {
	    			   breakBarrier();
	    		   }
	    	   }
	       }
	
	       //如果计数器不为0则执行此循环
	       for (;;) {
	    	   try {
	    		   //根据传入的参数来决定是定时等待还是非定时等待
	    		   if (!timed) {
	    			   trip.await();
	    		   }else if (nanos > 0L) {
	    			   nanos = trip.awaitNanos(nanos);
	    		   }
	    	   } catch (InterruptedException ie) {
	    		   //若当前线程在等待期间被中断则打翻栅栏唤醒其他线程
	    		   if (g == generation && ! g.broken) {
	    			   breakBarrier();
	    			   throw ie;
	    		   } else {
	    			   //若在捕获中断异常前已经完成在栅栏上的等待, 则直接调用中断操作
	    			   Thread.currentThread().interrupt();
	    		   }
	    	   }
	    	   //如果线程因为打翻栅栏操作而被唤醒则抛出异常
	    	   if (g.broken) {
	    		   throw new BrokenBarrierException();
	    	   }
	    	   //如果线程因为换代操作而被唤醒则返回计数器的值
	    	   if (g != generation) {
	    		   return index;
	    	   }
	    	   //如果线程因为时间到了而被唤醒则打翻栅栏并抛出异常
	    	   if (timed && nanos <= 0L) {
	    		   breakBarrier();
	    		   throw new TimeoutException();
	    	   }
	       }
		} finally {
			lock.unlock();
		}
	}

	//构造器1
	public CyclicBarrier(int parties, Runnable barrierAction) {
	    if (parties <= 0) throw new IllegalArgumentException();
	    this.parties = parties;
	    this.count = parties;
	    this.barrierCommand = barrierAction;
	}
	
	//构造器2
	public CyclicBarrier(int parties) {
	    this(parties, null);
	}

    //返回需要阻塞线程的数量
    public int getParties() {
        return parties;
    }

	//非定时等待
	public int await() throws InterruptedException, BrokenBarrierException {
	    try {
	        return dowait(false, 0L);
	    } catch (TimeoutException toe) {
	        throw new Error(toe);
	    }
	}
	
	//定时等待
	public int await(long timeout, TimeUnit unit) throws InterruptedException, BrokenBarrierException, TimeoutException {
	    return dowait(true, unit.toNanos(timeout));
	}

    //查询当前栅栏是否打翻
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    //重置栅栏状态
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//打翻当前栅栏
            breakBarrier();
            //转到下一代
            nextGeneration();
        } finally {
            lock.unlock();
        }
    }

    //返回当前拦截的线程数
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
