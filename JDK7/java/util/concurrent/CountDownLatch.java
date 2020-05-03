package jdk7.util.concurrent;

import java.util.concurrent.TimeUnit;

import jdk7.util.concurrent.locks.AbstractQueuedSynchronizer;

public class CountDownLatch {
	
	//同步器
	private static final class Sync extends AbstractQueuedSynchronizer {
	
		//构造器
	    Sync(int count) {
	        setState(count);
	    }
	
	    //获取当前同步状态
	    int getCount() {
	        return getState();
	    }
	
	    //尝试获取锁
	    //返回负数：表示当前线程获取失败
		//返回零值：表示当前线程获取成功, 但是后继线程不能再获取了
		//返回正数：表示当前线程获取成功, 并且后继线程同样可以获取成功
	    protected int tryAcquireShared(int acquires) {
	        return (getState() == 0) ? 1 : -1;
	    }
	
	    //尝试释放锁
	    protected boolean tryReleaseShared(int releases) {
	        for (;;) {
	        	//获取同步状态
	            int c = getState();
	            //如果同步状态为0, 则不能再释放了
	            if (c == 0) {
	            	return false;
	            }
	            //否则的话就将同步状态减1
	            int nextc = c-1;
	            //使用CAS方式更新同步状态
	            if (compareAndSetState(c, nextc)) {
	            	return nextc == 0;
	            }
	        }
	    }
	}

    private final Sync sync;

	//构造器
	public CountDownLatch(int count) {
	    if (count < 0) throw new IllegalArgumentException("count < 0");
	    this.sync = new Sync(count);
	}

	//导致当前线程等待, 直到门闩减少到0, 或者线程被打断
	public void await() throws InterruptedException {
		//以响应线程中断方式获取
	    sync.acquireSharedInterruptibly(1);
	}
    
    //设置超时时间的等待
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

	//减少门闩的方法
	public void countDown() {
	    sync.releaseShared(1);
	}

    //返回当前门闩的数量
    public long getCount() {
        return sync.getCount();
    }

    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
