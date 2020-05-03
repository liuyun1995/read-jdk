package jdk7.util.concurrent.locks;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class ReentrantLock implements Lock, java.io.Serializable {
	
	//同步器
	private final Sync sync;

	abstract static class Sync extends AbstractQueuedSynchronizer {
	
	    //获取锁的操作
	    abstract void lock();
	
		//非公平的获取锁
		final boolean nonfairTryAcquire(int acquires) {
			//获取当前线程
		    final Thread current = Thread.currentThread();
		    //获取当前同步状态
		    int c = getState();
		    //如果同步状态为0则表明锁没有被占用
		    if (c == 0) {
		    	//使用CAS更新同步状态
		        if (compareAndSetState(0, acquires)) {
		        	//设置目前占用锁的线程
		            setExclusiveOwnerThread(current);
		            return true;
		        }
		    //否则的话就判断持有锁的是否是当前线程
		    }else if (current == getExclusiveOwnerThread()) {
		    	//如果锁是被当前线程持有的, 就直接修改当前同步状态
		        int nextc = c + acquires;
		        if (nextc < 0) {
		        	throw new Error("Maximum lock count exceeded");
		        }
		        setState(nextc);
		        return true;
		    }
		    //如果持有锁的不是当前线程则返回失败标志
		    return false;
		}
	
		//尝试释放锁
		protected final boolean tryRelease(int releases) {
		    int c = getState() - releases;
		    //如果持有锁的线程不是当前线程就抛出异常
		    if (Thread.currentThread() != getExclusiveOwnerThread()) {
		    	throw new IllegalMonitorStateException();
		    }
		    boolean free = false;
		    //如果同步状态为0则表明锁被释放
		    if (c == 0) {
		    	//设置锁被释放的标志为真
		        free = true;
		        //设置占用线程为空
		        setExclusiveOwnerThread(null);
		    }
		    setState(c);
		    return free;
		}
	
	    //判断锁是否被当前线程所持有
	    protected final boolean isHeldExclusively() {
	        return getExclusiveOwnerThread() == Thread.currentThread();
	    }
	
	    //新建Condition对象
	    final ConditionObject newCondition() {
	        return new ConditionObject();
	    }
	
	    //获取持有锁的线程
	    final Thread getOwner() {
	        return getState() == 0 ? null : getExclusiveOwnerThread();
	    }
	
	    //获取重入的次数
	    final int getHoldCount() {
	        return isHeldExclusively() ? getState() : 0;
	    }
	
	    //判断锁是否被锁上
	    final boolean isLocked() {
	        return getState() != 0;
	    }
	
	    //用于序列化
	    private void readObject(jdk7.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
	        s.defaultReadObject();
	        setState(0);
	    }
	    
	}

	//实现非公平锁的同步器
	static final class NonfairSync extends Sync {
	    //实现父类的抽象获取锁的方法
	    final void lock() {
	    	//使用CAS方式设置同步状态
	        if (compareAndSetState(0, 1)) {
	        	//如果设置成功则表明锁没被占用
	        	setExclusiveOwnerThread(Thread.currentThread());
	        } else {
	        	//否则表明锁已经被占用, 调用acquire让线程去同步队列排队获取
	        	acquire(1);
	        }
	    }
	    //尝试获取锁的方法
	    protected final boolean tryAcquire(int acquires) {
	        return nonfairTryAcquire(acquires);
	    }
	}

	//实现公平锁的同步器
	static final class FairSync extends Sync {
		//实现父类的抽象获取锁的方法
	    final void lock() {
	    	//调用acquire让线程去同步队列排队获取
	        acquire(1);
	    }
	    //尝试获取锁的方法
	    protected final boolean tryAcquire(int acquires) {
	    	//获取当前线程
	        final Thread current = Thread.currentThread();
	        //获取当前同步状态
	        int c = getState();
	        //如果同步状态0则表示锁没被占用
	        if (c == 0) {
	        	//判断同步队列是否有前继结点
	            if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
	            	//如果没有前继结点且设置同步状态成功就表示获取锁成功
	                setExclusiveOwnerThread(current);
	                return true;
	            }
	        //否则判断是否是当前线程持有锁
	        }else if (current == getExclusiveOwnerThread()) {
	        	//如果是当前线程持有锁就直接修改同步状态
	            int nextc = c + acquires;
	            if (nextc < 0) {
	            	throw new Error("Maximum lock count exceeded");
	            }
	            setState(nextc);
	            return true;
	        }
	        //如果不是当前线程持有锁则获取失败
	        return false;
	    }
	}

	//构造器1
	public ReentrantLock() {
	    sync = new NonfairSync();
	}
	
	//构造器2
	public ReentrantLock(boolean fair) {
	    sync = fair ? new FairSync() : new NonfairSync();
	}

	//获取锁的操作
	public void lock() {
	    sync.lock();
	}

    //可中断的获取锁
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    //尝试获取锁
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    //设置超时时间获取, 会自旋
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

	//释放锁的操作
	public void unlock() {
	    sync.release(1);
	}

	//创建条件队列
	public Condition newCondition() {
	    return sync.newCondition();
	}

    //获取锁的重入数
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    //判断锁是否被当前线程持有
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    //判断锁是否被锁上
    public boolean isLocked() {
        return sync.isLocked();
    }

    //判断是否是公平锁
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    //获取持有锁的线程
    protected Thread getOwner() {
        return sync.getOwner();
    }

    //查询是否有其他线程在等待获取锁
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    //查询给定的线程是否在排队等待获取锁
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    //返回同步队列的长度
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    //返回同步队列中所有等待的线程集合
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    //查询指定的条件队列是否有线程在等待
    public boolean hasWaiters(Condition condition) {
        if (condition == null) {
        	throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
        	throw new IllegalArgumentException("not owner");
        }
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    //返回指定条件队列的长度
    public int getWaitQueueLength(Condition condition) {
        if (condition == null) {
        	throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
        	throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    //返回在条件队列中等待的线程集合
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null) {
        	throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
        	throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }
}
