package jdk7.util.concurrent;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import jdk7.util.concurrent.locks.AbstractQueuedSynchronizer;

public class Semaphore implements java.io.Serializable {
	
    private final Sync sync;

    abstract static class Sync extends AbstractQueuedSynchronizer {
    	
        Sync(int permits) {
            setState(permits);
        }

        //获取可用许可证
        final int getPermits() {
            return getState();
        }

		//非公平方式尝试获取
		final int nonfairTryAcquireShared(int acquires) {
		    for (;;) {
		    	//获取可用许可证
		        int available = getState();
		        //获取剩余许可证
		        int remaining = available - acquires;
		        //1.如果remaining小于0则直接返回remaining
		        //2.如果remaining大于0则先更新同步状态再返回remaining
		        if (remaining < 0 || compareAndSetState(available, remaining)) {
		        	return remaining;
		        }
		    }
		}

		//尝试释放操作
		protected final boolean tryReleaseShared(int releases) {
		    for (;;) {
		    	//获取当前同步状态
		        int current = getState();
		        //将当前同步状态加上传入的参数
		        int next = current + releases;
		        //如果相加结果小于当前同步状态的话就报错
		        if (next < current) {
		        	throw new Error("Maximum permit count exceeded");
		        }
		        //以CAS方式更新同步状态的值, 更新成功则返回true, 否则继续循环
		        if (compareAndSetState(current, next)) {
		        	return true;
		        }
		    }
		}

        //减少许可证
        final void reducePermits(int reductions) {
            for (;;) {
            	//获取当前许可证
                int current = getState();
                //将当前许可证减去传入的数值
                int next = current - reductions;
                //如果减去之后的值大于当前数则报错
                if (next > current) {
                	throw new Error("Permit count underflow");
                }
                //更新同步状态为减去之后的值
                if (compareAndSetState(current, next)) {
                	return;
                }
            }
        }

        //清空许可证
        final int drainPermits() {
            for (;;) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0)) {
                	return current;
                }
            }
        }
    }

	//非公平同步器
	static final class NonfairSync extends Sync {
	    private static final long serialVersionUID = -2694183684443567898L;
	
	    NonfairSync(int permits) {
	        super(permits);
	    }
	
	    //尝试获取许可证
	    protected int tryAcquireShared(int acquires) {
	        return nonfairTryAcquireShared(acquires);
	    }
	}
	
	//公平同步器
	static final class FairSync extends Sync {
	    private static final long serialVersionUID = 2014338818796000944L;
	
	    FairSync(int permits) {
	        super(permits);
	    }
	
	    //尝试获取许可证
	    protected int tryAcquireShared(int acquires) {
	        for (;;) {
	        	//判断同步队列前面有没有人排队
	            if (hasQueuedPredecessors()) {
	            	//如果有的话就直接返回-1，表示尝试获取失败
	            	return -1;
	            }
	            //下面操作和非公平获取一样
	            int available = getState();
	            int remaining = available - acquires;
	            if (remaining < 0 || compareAndSetState(available, remaining)) {
	            	return remaining;
	            }
	        }
	    }
	}

	//构造器1
	public Semaphore(int permits) {
	    sync = new NonfairSync(permits);
	}
	
	//构造器2
	public Semaphore(int permits, boolean fair) {
	    sync = fair ? new FairSync(permits) : new NonfairSync(permits);
	}

    /************************************************获取或释放一个许可证**************************************************/
    
	//获取一个许可证(响应中断)
	public void acquire() throws InterruptedException {
	    sync.acquireSharedInterruptibly(1);
	}
	
	//获取一个许可证(不响应中断)
	public void acquireUninterruptibly() {
	    sync.acquireShared(1);
	}
	
	//尝试获取许可证(非公平获取)
	public boolean tryAcquire() {
	    return sync.nonfairTryAcquireShared(1) >= 0;
	}
	
	//尝试获取许可证(定时获取)
	public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
	    return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
	}

	//释放一个许可证
	public void release() {
	    sync.releaseShared(1);
	}

    /************************************************获取或释放多个许可证**************************************************/
    
    //获取许可证(响应中断)
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }

    //获取许可证(不响应中断)
    public void acquireUninterruptibly(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireShared(permits);
    }

    //尝试获取许可证(非公平获取)
    public boolean tryAcquire(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.nonfairTryAcquireShared(permits) >= 0;
    }

    //尝试获取许可证(定时获取)
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
    }

    //释放许可证
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }
    
    /************************************************其他支持操作**************************************************/

    //获取剩余许可证
    public int availablePermits() {
        return sync.getPermits();
    }

    //清空许可证
    public int drainPermits() {
        return sync.drainPermits();
    }

    //减少许可证
    protected void reducePermits(int reduction) {
        if (reduction < 0) throw new IllegalArgumentException();
        sync.reducePermits(reduction);
    }

    //判断是否是公平获取
    public boolean isFair() {
        return sync instanceof FairSync;
    }

    //判断同步队列中是否有线程在等待
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    //获取同步队列的长度
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    //获取排队线程的集合
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    public String toString() {
        return super.toString() + "[Permits = " + sync.getPermits() + "]";
    }
}
