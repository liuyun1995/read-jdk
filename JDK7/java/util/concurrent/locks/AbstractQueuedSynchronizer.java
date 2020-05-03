package jdk7.util.concurrent.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    protected AbstractQueuedSynchronizer() {}  

	//同步队列的结点
	static final class Node {
	    
	    static final Node SHARED = new Node(); //表示当前线程以共享模式持有锁
	    
	    static final Node EXCLUSIVE = null;    //表示当前线程以独占模式持有锁
	
	    static final int CANCELLED =  1;       //表示当前结点已经取消获取锁
	    
	    static final int SIGNAL    = -1;       //表示后继结点的线程需要运行
	    
	    static final int CONDITION = -2;       //表示当前结点在条件队列中排队
	    
	    static final int PROPAGATE = -3;       //表示当前结点需要将锁可以获取的状态告诉后继结点
	
	    volatile int waitStatus; //表示当前结点的等待状态
	   
	    volatile Node prev;      //表示同步队列中的前继结点
	
	    volatile Node next;      //表示同步队列中的后继结点  
	
	    volatile Thread thread;  //当前结点持有的线程引用
	    
	    Node nextWaiter;         //指向条件队列的下一个等待者
	
	    //返回下一个等待者是否是共享模式
	    final boolean isShared() {
	        return nextWaiter == SHARED;
	    }
	
	    //返回当前结点的前继结点
	    final Node predecessor() throws NullPointerException {
	        Node p = prev;
	        if (p == null) {
	        	throw new NullPointerException();
	        } else {
	        	return p;
	        }
	    }
	    
	    //构造器1
	    Node() {}
	    
	    //构造器2, 默认用这个构造器
	    Node(Thread thread, Node mode) {
	    	//将持有模式设置为条件队列的下一个等待者
	        this.nextWaiter = mode;
	        this.thread = thread;
	    }
	    
	    //构造器3, 只在addConditionWaiter方法用到
	    Node(Thread thread, int waitStatus) {
	        this.waitStatus = waitStatus;
	        this.thread = thread;
	    }
	}

	//同步队列的头结点
	private transient volatile Node head; 
	
	//同步队列的尾结点
	private transient volatile Node tail;
	
	//同步状态
	private volatile int state;
	
	//获取同步状态
	protected final int getState() {
	    return state;
	}
	
	//设置同步状态
	protected final void setState(int newState) {
	    state = newState;
	}
	
	//以CAS方式设置同步状态
	protected final boolean compareAndSetState(int expect, int update) {
	    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
	}

    //以下为排队措施

    //自旋时间, 使用自旋而不是直接进入等待队列可以提高响应性
    static final long spinForTimeoutThreshold = 1000L;

    /**************************************************基本入队操作***********************************************/
    
    //将当前线程包装成结点并添加到同步队列尾部
  	private Node addWaiter(Node mode) {
  		//指定持有锁的模式
  	    Node node = new Node(Thread.currentThread(), mode);
  	    //获取同步队列尾结点引用
  	    Node pred = tail;
  	    //如果尾结点不为空, 表明同步队列已存在结点
  	    if (pred != null) {
  	    	//1.指向当前尾结点
  	        node.prev = pred;
  	        //2.设置当前结点为尾结点
  	        if (compareAndSetTail(pred, node)) {
  	        	//3.将旧的尾结点的后继指向新的尾结点
  	            pred.next = node;
  	            return node;
  	        }
  	    }
  	    //否则表明同步队列还没有进行初始化
  	    enq(node);
  	    return node;
  	}
    
	//结点入队操作, 返回前一个结点
	private Node enq(final Node node) {
	    for (;;) {
	    	//获取同步队列尾结点引用
	        Node t = tail;
	        //如果尾结点为空说明同步队列还没有初始化
	        if (t == null) {
	        	//初始化同步队列
	            if (compareAndSetHead(new Node())) {
	            	tail = head;
	            }
	        } else {
	        	//1.指向当前尾结点
	            node.prev = t;
	            //2.设置当前结点为尾结点
	            if (compareAndSetTail(t, node)) {
	            	//3.将旧的尾结点的后继指向新的尾结点
	                t.next = node;
	                return t;
	            }
	        }
	    }
	}

    /**************************************************基本出队操作***********************************************/
    
	//独占模式出队操作
	private void unparkSuccessor(Node node) {
		//获取给定结点的等待状态
	    int ws = node.waitStatus;
	    //将等待状态更新为0
	    if (ws < 0) {
	    	compareAndSetWaitStatus(node, ws, 0);
	    }
	    //获取给定结点的后继结点
	    Node s = node.next;
	    //后继结点为空或者等待状态为取消状态
	    if (s == null || s.waitStatus > 0) {
	        s = null;
	        //从后向前遍历队列找到第一个不是取消状态的结点
	        for (Node t = tail; t != null && t != node; t = t.prev) {
	        	if (t.waitStatus <= 0) {
	        		s = t;
	        	}
	        }
	    }
	    //唤醒给定结点后面首个不是取消状态的结点
	    if (s != null) {
	    	LockSupport.unpark(s.thread);
	    }
	}

	//释放锁的操作(共享模式)
	private void doReleaseShared() {
	    for (;;) {
	    	//获取同步队列的head结点
	        Node h = head;
	        if (h != null && h != tail) {
	        	//获取head结点的等待状态
	            int ws = h.waitStatus;
	            //如果head结点的状态为SIGNAL, 表明后面有人在排队
	            if (ws == Node.SIGNAL) {
	            	//先把head结点的等待状态更新为0
	                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
	                	continue;
	                }
	                //再去唤醒后继结点
	                unparkSuccessor(h);
	             //如果head结点的状态为0, 表明此时后面没人在排队, 就只是将head状态修改为PROPAGATE
	            }else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
	            	continue;
	            }
	        }
	        //只有保证期间head结点没被修改过才能跳出循环
	        if (h == head) {
	        	break;
	        }
	    }
	}

    /**************************************************取消获取操作***********************************************/
    
    //设置指定结点取消获取锁的操作
    private void cancelAcquire(Node node) {
        if (node == null) {
        	return;
        }
        //将给定结点的线程置空
        node.thread = null;
        //获取给定结点的前继结点引用
        Node pred = node.prev;
        //如果前继结点取消了获取操作, 就循环的找到下一个前继结点
        while (pred.waitStatus > 0) {
        	node.prev = pred = pred.prev;
        }
        
        //获取找到的前继结点的后结点
        Node predNext = pred.next;
        //将给定结点的状态设置为取消状态
        node.waitStatus = Node.CANCELLED;

        //如果给定结点是尾结点, 就只要将自己从同步队列移除
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            int ws;
            //if操作主要是判断前继结点是否知道后继结点需要运行, 如果不知道就通知它
            if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && pred.thread != null) {
            	//获取当前结点的后继结点
                Node next = node.next;
                //这里是将当前结点从同步队列移除的操作, 也就是将前继结点的后继结点引用设置为当前结点的后继
                if (next != null && next.waitStatus <= 0) {
                	compareAndSetNext(pred, predNext, next);
                }
            } else {
            	//唤醒后继结点
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }
    
    /**************************************************转移头结点操作***********************************************/
    
    //设置head结点(独占模式)
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }
    
	//设置head结点并传播锁的状态(共享模式)
	private void setHeadAndPropagate(Node node, int propagate) {
	    Node h = head;
	    //将给定结点设置为head结点
	    setHead(node);
	    //如果propagate大于0表明锁可以获取了
	    if (propagate > 0 || h == null || h.waitStatus < 0) {
	    	//获取给定结点的后继结点
	        Node s = node.next;
	        //如果给定结点的后继结点为空, 或者它的状态是共享状态
	        if (s == null || s.isShared()) {
	        	//唤醒后继结点
	        	doReleaseShared();
	        }
	    }
	}

    /**************************************************挂起线程支持方法***********************************************/
    
	//判断是否可以将当前结点挂起
	private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
		//获取前继结点的等待状态
	    int ws = pred.waitStatus;
	    //如果前继结点状态为SIGNAL, 表明前继结点会唤醒当前结点, 所以当前结点可以安心的挂起了
	    if (ws == Node.SIGNAL) {
	    	return true;
	    }
	    
	    if (ws > 0) {
	    	//下面的操作是清理同步队列中所有已取消的前继结点
	        do {
	            node.prev = pred = pred.prev;
	        } while (pred.waitStatus > 0);
	        pred.next = node;
	    } else {
	    	//到这里表示前继结点状态不是SIGNAL, 很可能还是等于0, 这样的话前继结点就不会去唤醒当前结点了
	    	//所以当前结点必须要确保前继结点的状态为SIGNAL才能安心的挂起自己
	        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
	    }
	    return false;
	}

	//当前线程将自己中断
	private static void selfInterrupt() {
	    Thread.currentThread().interrupt();
	}

	//挂起当前线程
	private final boolean parkAndCheckInterrupt() {
	    LockSupport.park(this);
	    return Thread.interrupted();
	}
	
	//判断同步器是否只被当前线程持有
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }
    
    /***************************************************独占模式***************************************************/
    /************************************************************************************************************/
    /***********************************************独占模式(不可中断获取)**********************************************/
	
    //尝试去获取锁(独占模式)
  	protected boolean tryAcquire(int arg) {
  	    throw new UnsupportedOperationException();
  	}
    
	//以不可中断模式获取锁(独占模式)
	public final void acquire(int arg) {
	    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
	    	selfInterrupt();
	    }
	}
	
	//以不可中断方式获取锁(独占模式)
  	final boolean acquireQueued(final Node node, int arg) {
  	    boolean failed = true;
  	    try {
  	        boolean interrupted = false;
  	        for (;;) {
  	        	//获取给定结点的前继结点的引用
  	            final Node p = node.predecessor();
  	            //如果当前结点是同步队列的第一个结点, 就尝试去获取锁
  	            if (p == head && tryAcquire(arg)) {
  	            	//将给定结点设置为head结点
  	                setHead(node);
  	                //为了帮助垃圾收集, 将上一个head结点的后继清空
  	                p.next = null;
  	                //设置获取成功状态
  	                failed = false;
  	                //返回中断的状态, 整个循环执行到这里才是出口
  	                return interrupted;
  	            }
  	            //否则说明锁的状态还是不可获取, 这时判断是否可以挂起当前线程
  	            //如果判断结果为真则挂起当前线程, 否则继续循环, 在这期间线程不响应中断
  	            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
  	            	//线程被唤醒后即时发现中断请求也不会退出循环
  	            	interrupted = true;
  	            }
  	        }
  	    } finally {
  	    	//最后确保如果获取失败就执行取消操作
  	        if (failed) {
  	        	cancelAcquire(node);
  	        }
  	    }
  	}
	
  	/***********************************************独占模式(可中断获取)**********************************************/
	
	//以可中断模式获取获取锁(独占模式)
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
        	throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
        	doAcquireInterruptibly(arg);
        }
    }
    
	//以可中断模式获取锁(独占模式)
	private void doAcquireInterruptibly(int arg) throws InterruptedException {
		//将当前线程包装成结点添加到同步队列中
		final Node node = addWaiter(Node.EXCLUSIVE);
		boolean failed = true;
		try {
			for (;;) {
				//获取当前结点的前继结点
				final Node p = node.predecessor();
				//如果p是head结点, 那么当前线程就再次尝试获取锁
				if (p == head && tryAcquire(arg)) {
					setHead(node);
					p.next = null; // help GC
					failed = false;
					//获取锁成功后返回
					return;
				}
				//如果满足条件就挂起当前线程, 此时响应中断并抛出异常
				if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
					//线程被唤醒后如果发现中断请求就抛出异常
					throw new InterruptedException();
				}
			}
		} finally {
			if (failed) {
				cancelAcquire(node);
			}
		}
	}
    
  	/***********************************************独占模式(超时时间获取)**********************************************/
    
    //以限定超时时间获取锁(独占模式)
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
        	throw new InterruptedException();
        }
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }
      
	//以限定超时时间获取锁(独占模式)
	private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
		//获取系统当前时间
		long lastTime = System.nanoTime();
		//将当前线程包装成结点添加到同步队列中
		final Node node = addWaiter(Node.EXCLUSIVE);
		boolean failed = true;
		try {
			for (;;) {
				//获取当前结点的前继结点
				final Node p = node.predecessor();
				//如果前继是head结点, 那么当前线程就再次尝试获取锁
				if (p == head && tryAcquire(arg)) {
					//更新head结点
					setHead(node);
					p.next = null;
					failed = false;
					return true;
				}
				//超时时间用完了就直接退出循环
				if (nanosTimeout <= 0) {
					return false;
				}
				//如果超时时间大于自旋时间, 那么等判断可以挂起线程之后就会将线程挂起一段时间
				if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
					//将当前线程挂起一段时间, 之后再自己醒来
					LockSupport.parkNanos(this, nanosTimeout);
				}
				//获取系统当前时间
				long now = System.nanoTime();
				//超时时间每次都减去获取锁的时间间隔
				nanosTimeout -= now - lastTime;
				//再次更新lastTime
				lastTime = now;
				//在获取锁的期间收到中断请求就抛出异常
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
			}
		} finally {
			if (failed) {
				cancelAcquire(node);
			}
		}
	}
  	
  	/***********************************************独占模式(出队操作)**********************************************/
    
    //尝试去释放锁(独占模式), 相当于拨动密码锁, 至于什么时候开锁得让子类决定
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

	//释放锁的操作(独占模式)
	public final boolean release(int arg) {
		//拨动密码锁, 看看是否能够开锁
	    if (tryRelease(arg)) {
	    	//获取head结点
	        Node h = head;
	        //如果head结点不为空并且等待状态不等于0就去唤醒后继结点
	        if (h != null && h.waitStatus != 0) {
	        	//唤醒后继结点
	        	unparkSuccessor(h);
	        }
	        return true;
	    }
	    return false;
	}
	
	/****************************************************共享模式***************************************************/
	/************************************************************************************************************/
	/***********************************************共享模式(不可中断获取)**********************************************/

	//尝试去获取锁(共享模式)
	//负数：表示获取失败
	//零值：表示当前结点获取成功, 但是后继结点不能再获取了
	//正数：表示当前结点获取成功, 并且后继结点同样可以获取成功
	protected int tryAcquireShared(int arg) {
	    throw new UnsupportedOperationException();
	}
	
	//以不可中断模式获取锁(共享模式)
	public final void acquireShared(int arg) {
		//1.尝试去获取锁
	    if (tryAcquireShared(arg) < 0) {
	    	//2.如果获取失败就进入这个方法
	    	doAcquireShared(arg);
	    }
	}
	
	//在同步队列中获取(共享模式)
	private void doAcquireShared(int arg) {
		//添加到同步队列中
	    final Node node = addWaiter(Node.SHARED);
	    boolean failed = true;
	    try {
	        boolean interrupted = false;
	        for (;;) {
	        	//获取当前结点的前继结点
	            final Node p = node.predecessor();
	            //如果前继结点为head结点就再次尝试去获取锁
	            if (p == head) {
	            	//再次尝试去获取锁并返回获取状态
	            	//r < 0, 表示获取失败
	                //r = 0, 表示当前结点获取成功, 但是后继结点不能再获取了
	                //r > 0, 表示当前结点获取成功, 并且后继结点同样可以获取成功
	                int r = tryAcquireShared(arg);
	                if (r >= 0) {
	                	//到这里说明当前结点已经获取锁成功了, 此时它会将锁的状态信息传播给后继结点
	                    setHeadAndPropagate(node, r);
	                    p.next = null;
	                    //如果在线程阻塞期间收到中断请求, 就在这一步响应该请求
	                    if (interrupted) {
	                    	selfInterrupt();
	                    }
	                    failed = false;
	                    return;
	                }
	            }
	            //每次获取锁失败后都会判断是否可以将线程挂起, 如果可以的话就会在parkAndCheckInterrupt方法里将线程挂起
	            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
	            	interrupted = true;
	            }
	        }
	    } finally {
	        if (failed) {
	        	cancelAcquire(node);
	        }
	    }
	}

    /***********************************************共享模式(可中断获取)**********************************************/
    
	//以可中断模式获取锁(共享模式)
	public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
		//首先判断线程是否中断, 如果是则抛出异常
	    if (Thread.interrupted()) {
	    	throw new InterruptedException();
	    }
	    //1.尝试去获取锁
	    if (tryAcquireShared(arg) < 0) {
	    	//2. 如果获取失败则进人该方法
	    	doAcquireSharedInterruptibly(arg);
	    }
	}
	
	//以可中断模式获取(共享模式)
	private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
		//将当前结点插入同步队列尾部
	    final Node node = addWaiter(Node.SHARED);
	    boolean failed = true;
	    try {
	        for (;;) {
	        	//获取当前结点的前继结点
	            final Node p = node.predecessor();
	            if (p == head) {
	                int r = tryAcquireShared(arg);
	                if (r >= 0) {
	                    setHeadAndPropagate(node, r);
	                    p.next = null;
	                    failed = false;
	                    return;
	                }
	            }
	            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
	            	//如果线程在阻塞过程中收到过中断请求, 那么就会立马在这里抛出异常
	            	throw new InterruptedException();
	            }
	        }
	    } finally {
	        if (failed) {
	        	cancelAcquire(node);
	        }
	    }
	}
    
    /***********************************************共享模式(超时时间获取)**********************************************/

	//以限定超时时间获取锁(共享模式)
	public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
	    if (Thread.interrupted()) {
	    	throw new InterruptedException();
	    }
	    //1.调用tryAcquireShared尝试去获取锁
	    //2.如果获取失败就调用doAcquireSharedNanos
	    return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
	}
	
	//以限定超时时间获取锁(共享模式)
	private boolean doAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
	    long lastTime = System.nanoTime();
	    final Node node = addWaiter(Node.SHARED);
	    boolean failed = true;
	    try {
	        for (;;) {
	        	//获取当前结点的前继结点
	            final Node p = node.predecessor();
	            if (p == head) {
	                int r = tryAcquireShared(arg);
	                if (r >= 0) {
	                    setHeadAndPropagate(node, r);
	                    p.next = null;
	                    failed = false;
	                    return true;
	                }
	            }
	            //如果超时时间用完了就结束获取, 并返回失败信息
	            if (nanosTimeout <= 0) {
	            	return false;
	            }
	            //1.检查是否满足将线程挂起要求(保证前继结点状态为SIGNAL)
	            //2.检查超时时间是否大于自旋时间
	            if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
	            	//若满足上面两个条件就将当前线程挂起一段时间
	            	LockSupport.parkNanos(this, nanosTimeout);
	            }
	            long now = System.nanoTime();
	            //超时时间每次减去获取锁的时间
	            nanosTimeout -= now - lastTime;
	            lastTime = now;
	            //如果在阻塞时收到中断请求就立马抛出异常
	            if (Thread.interrupted()) {
	            	throw new InterruptedException();
	            }
	        }
	    } finally {
	        if (failed) {
	        	cancelAcquire(node);
	        }
	    }
	}
	
    /***********************************************共享模式(出队操作)**********************************************/

	//尝试去释放锁(共享模式)
	protected boolean tryReleaseShared(int arg) {
	    throw new UnsupportedOperationException();
	}
	
	//释放锁的操作(共享模式)
	public final boolean releaseShared(int arg) {
		//1.尝试去释放锁
	    if (tryReleaseShared(arg)) {
	    	//2.如果释放成功就唤醒其他线程
	        doReleaseShared();
	        return true;
	    }
	    return false;
	}
    
    /**********************************************以下方法是针对同步队列的一些方法************************************/

    //判断同步队列中是否有线程在排队
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    //判断是否有其他线程在争夺同步器
    public final boolean hasContended() {
        return head != null;
    }

    
    //获取同步队列中第一个线程(等待时间最长)
    public final Thread getFirstQueuedThread() {
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    //获取同步队列的第一个线程
    private Thread fullGetFirstQueuedThread() {
        Node h, s;
        Thread st;
        //获取head结点的后继结点的线程, 并发操作中如果获取失败会再尝试一次
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;
        
        //如果到这里还没有获取到, 就从后向前遍历同步队列
        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null) {
            	firstThread = tt;
            }
            t = t.prev;
        }
        return firstThread;
    }

    
    //判断指定线程是否在同步队列排队
    public final boolean isQueued(Thread thread) {
        if (thread == null) {
        	throw new NullPointerException();
        }
        for (Node p = tail; p != null; p = p.prev) {
        	if (p.thread == thread) {
        		return true;
        	}
        }
        return false;
    }

    
    //判断第一个结点是否在独占模式下排队
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    //判断同步队列中是否有线程比当前线程等待的时间更长
    public final boolean hasQueuedPredecessors() {
    	//以反向初始化顺序读取字段
        Node t = tail;
        Node h = head;
        Node s;
        //判断head结点的后继结点的线程是否是当前线程, 如果不是则代表当前线程前面还有线程在排队
        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    //获取同步队列的长度
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null) {
            	++n;
            }
        }
        return n;
    }

    //获取同步队列中所有排队的线程
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null) {
            	list.add(t);
            }
        }
        return list;
    }

    
    //获取在同步队列中独占模式的线程
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                	list.add(t);
                }
            }
        }
        return list;
    }

    //获取同步队列中共享模式的线程
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                	list.add(t);
                }
            }
        }
        return list;
    }

    
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() + "[State = " + s + ", " + q + "empty queue]";
    }


    /************************************************条件队列的内部支持方法*****************************************/

    //判断指定结点是否在同步队列中
    final boolean isOnSyncQueue(Node node) {
    	//如果结点状态为contition表明该结点已在条件队列中
    	//因为同步队列是双向链表, 当结点没有前继的时候也表明不在同步队列中
        if (node.waitStatus == Node.CONDITION || node.prev == null) {
        	return false;
        }
        //如果当前结点有后继结点则表明在同步队列中
        if (node.next != null) {
        	return true;
        }
        //当前结点的前继结点可能是非空, 但因为CAS操作的失败导致该结点仍然不在同步队列中,
        //所以必须从后往前遍历队列去寻找这个结点
        return findNodeFromTail(node);
    }

    //判断指定结点是否在同步队列中(遍历同步队列)
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node) {
            	return true;
            }
            if (t == null) {
            	return false;
            }
            t = t.prev;
        }
    }
    
	//将指定结点从条件队列转移到同步队列中
	final boolean transferForSignal(Node node) {
		//将等待状态从CONDITION设置为0
	    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
	    	//如果更新状态的操作失败就直接返回false
	    	//可能是transferAfterCancelledWait方法先将状态改变了, 导致这步CAS操作失败
	    	return false;
	    }
	    //将该结点添加到同步队列尾部
	    Node p = enq(node);
	    int ws = p.waitStatus;
	    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) {
	    	//出现以下情况就会唤醒当前线程
	    	//1.前继结点是取消状态
	    	//2.更新前继结点的状态为SIGNAL操作失败
	    	LockSupport.unpark(node.thread);
	    }
	    return true;
	}

	//将取消条件等待的结点从条件队列转移到同步队列中
	final boolean transferAfterCancelledWait(Node node) {
		//如果这步CAS操作成功的话就表明中断发生在signal方法之前
	    if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
	    	//状态修改成功后就将该结点放入同步队列尾部
	        enq(node);
	        return true;
	    }
	    //到这里表明CAS操作失败, 说明中断发生在signal方法之后
	    while (!isOnSyncQueue(node)) {
	    	//如果sinal方法还没有将结点转移到同步队列, 就通过自旋等待一下
	    	Thread.yield();
	    }
	    return false;
	}

	//完全释放锁
	final int fullyRelease(Node node) {
	    boolean failed = true;
	    try {
	    	//获取当前的同步状态
	        int savedState = getState();
	        //使用当前的同步状态去释放锁
	        if (release(savedState)) {
	            failed = false;
	            //如果释放锁成功就返回当前同步状态
	            return savedState;
	        } else {
	        	//如果释放锁失败就抛出运行时异常
	            throw new IllegalMonitorStateException();
	        }
	    } finally {
	    	//保证没有成功释放锁就将该结点设置为取消状态
	        if (failed) {
	        	node.waitStatus = Node.CANCELLED;
	        }
	    }
	}

    //判断给定的ConditionObject是否使用同步器作为它的锁
    public final boolean owns(ConditionObject condition) {
        if (condition == null) {
        	throw new NullPointerException();
        }
        return condition.isOwnedBy(this);
    }

    //返回是否有等待者
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition)) {
        	throw new IllegalArgumentException("Not owner");
        }
        return condition.hasWaiters();
    }

    
    //获取条件等待队列的长度
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition)) {
        	throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitQueueLength();
    }

    //获取条件队列中等待线程的集合
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition)) {
        	throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitingThreads();
    }

    //Condition队列, 这个队列不是必须的. 只有使用Condition时才会生成这条单向链表, 并且可能有多个Condition队列
    public class ConditionObject implements Condition, java.io.Serializable {
        
    	private static final long serialVersionUID = 1173984872572414699L;
        
        private transient Node firstWaiter; //条件队列的头结点
        
        private transient Node lastWaiter;  //条件队列的尾结点

        public ConditionObject() {}

		//添加结点到条件队列尾部
		private Node addConditionWaiter() {
			//获取条件队列的尾结点的引用
		    Node t = lastWaiter;
		    //检查条件队列中是否存在已取消的结点
		    if (t != null && t.waitStatus != Node.CONDITION) {
		    	//清除条件队列中所有取消条件等待的结点
		        unlinkCancelledWaiters();
		        t = lastWaiter;
		    }
		    //将当前线程包装成结点, 等待状态设置为CONDITION
		    Node node = new Node(Thread.currentThread(), Node.CONDITION);
		    //将新的结点添加到条件队列尾部
		    if (t == null) {
		    	firstWaiter = node;  
		    }else {
		    	t.nextWaiter = node; 
		    }
		    //更新尾结点引用
		    lastWaiter = node;
		    return node;
		}

		//清除条件队列中所有取消条件等待的结点
		private void unlinkCancelledWaiters() {
		    Node t = firstWaiter;
		    Node trail = null;
		    //从前向后遍历条件队列
		    while (t != null) {
		        Node next = t.nextWaiter;
		        //如果等待状态不是CONDITION
		        if (t.waitStatus != Node.CONDITION) {
		        	//1.将当前结点的后继结点引用置空
		            t.nextWaiter = null;
		            if (trail == null) {
		            	//2.前继结点的后继结点指向当前结点的后继结点
		            	firstWaiter = next;
		            }else {
		            	//2.前继结点的后继结点指向当前结点的后继结点
		            	trail.nextWaiter = next;
		            }
		            //这里表明已经到达了尾结点
		            if (next == null) {
		            	//更新尾结点引用
		            	lastWaiter = trail;
		            }
		        }else {
		        	//如果状态为CONDITION就更新追踪结点的引用
		        	trail = t;
		        }
		        //指向下一个结点
		        t = next;
		    }
		}

        /*********************************************唤醒条件队列单个结点*****************************************/
        
		//唤醒条件队列中的下一个结点
		public final void signal() {
			//判断当前线程是否持有锁
		    if (!isHeldExclusively()) {
		    	throw new IllegalMonitorStateException();
		    }
		    Node first = firstWaiter;
		    //如果条件队列中有排队者
		    if (first != null) {
		    	//唤醒条件队列中的头结点
		    	doSignal(first);
		    }
		}
		
		//唤醒条件队列中的头结点
		private void doSignal(Node first) {
		    do {
		    	//1.将firstWaiter引用向后移动一位
		        if ( (firstWaiter = first.nextWaiter) == null) {
		        	lastWaiter = null;
		        }
		        //2.将头结点的后继结点引用置空
		        first.nextWaiter = null;
		        //3.将头结点转移到同步队列, 转移完成后有可能唤醒线程
		        //4.如果transferForSignal操作失败就去唤醒下一个结点
		    } while (!transferForSignal(first) && (first = firstWaiter) != null);
		}

        /*********************************************唤醒条件队列全部结点*******************************************/
        
		//唤醒条件队列后面的全部结点
		public final void signalAll() {
			//判断当前线程是否持有锁
		    if (!isHeldExclusively()) {
		    	throw new IllegalMonitorStateException();
		    }
		    //获取条件队列头结点
		    Node first = firstWaiter;
		    if (first != null) {
		    	//唤醒条件队列的所有结点
		    	doSignalAll(first);
		    }
		}
		
		//唤醒条件队列的所有结点
		private void doSignalAll(Node first) {
			//先把头结点和尾结点的引用置空
		    lastWaiter = firstWaiter = null;
		    do {
		    	//先获取后继结点的引用
		        Node next = first.nextWaiter;
		        //把即将转移的结点的后继引用置空
		        first.nextWaiter = null;
		        //将结点从条件队列转移到同步队列
		        transferForSignal(first);
		        //将引用指向下一个结点
		        first = next;
		    } while (first != null);
		}


        /**********************************************线程中断检查*********************************************/

		//从条件等待中退出时再次挂起线程 
		private static final int REINTERRUPT =  1;
		//从条件等待中退出时马上抛出异常
		private static final int THROW_IE    = -1;

		//检查条件等待时的线程中断情况
		private int checkInterruptWhileWaiting(Node node) {
			//中断请求在signal操作之前：THROW_IE
			//中断请求在signal操作之后：REINTERRUPT
			//期间没有收到任何中断请求：0
		    return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
		}

		//结束条件等待后根据中断情况做出相应处理
		private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
			//如果中断模式是THROW_IE就抛出异常
		    if (interruptMode == THROW_IE) {
		    	throw new InterruptedException();
		    //如果中断模式是REINTERRUPT就自己挂起
		    } else if (interruptMode == REINTERRUPT) {
		    	selfInterrupt();
		    }
		}
        
        /*************************************************条件等待操作********************************************/

		//在条件队列中等待(不响应中断)
		public final void awaitUninterruptibly() {
			//将当前线程添加到条件队列尾部
		    Node node = addConditionWaiter();
		    //完全释放锁并返回当前同步状态
		    int savedState = fullyRelease(node);
		    boolean interrupted = false;
		    //结点一直在while循环里进行条件等待
		    while (!isOnSyncQueue(node)) {
		    	//条件队列中所有的线程都在这里被挂起
		        LockSupport.park(this);
		        //线程醒来发现中断并不会马上去响应
		        if (Thread.interrupted()) {
		        	interrupted = true;
		        }
		    }
		    if (acquireQueued(node, savedState) || interrupted) {
		    	//在这里响应所有中断请求, 满足以下两个条件之一就会将自己挂起
		    	//1.线程在条件等待时收到中断请求
		    	//2.线程在acquireQueued方法里收到中断请求
		    	selfInterrupt();
		    }
		}
        
		//在条件队列中等待(响应中断)
		public final void await() throws InterruptedException {
			//如果线程被中断则抛出异常
		    if (Thread.interrupted()) {
		    	throw new InterruptedException();
		    }
		    //将当前线程添加到条件队列尾部
		    Node node = addConditionWaiter();
		    //在进入条件等待之前先完全释放锁
		    int savedState = fullyRelease(node);
		    int interruptMode = 0;
			//线程一直在while循环里进行条件等待
			while (!isOnSyncQueue(node)) {
				//进行条件等待的线程都在这里被挂起, 线程被唤醒的情况有以下几种：
				//1.同步队列的前继结点已取消
				//2.设置同步队列的前继结点的状态为SIGNAL失败
				//3.前继结点释放锁后唤醒当前结点
			    LockSupport.park(this);
			    //当前线程醒来后立马检查是否被中断, 如果是则代表结点取消条件等待, 此时需要将结点移出条件队列
			    if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
			    	break;
			    }
			}
			//线程醒来后就会以独占模式获取锁
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			//这步操作主要为防止线程在signal之前中断而导致没与条件队列断绝联系
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			//根据中断模式进行响应的中断处理
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
		}

		//设置定时条件等待(相对时间), 不进行自旋等待
		public final long awaitNanos(long nanosTimeout) throws InterruptedException {
			//如果线程被中断则抛出异常
		    if (Thread.interrupted()) {
		    	throw new InterruptedException();
		    }
		    //将当前线程添加到条件队列尾部
		    Node node = addConditionWaiter();
		    //在进入条件等待之前先完全释放锁
		    int savedState = fullyRelease(node);
		    long lastTime = System.nanoTime();
		    int interruptMode = 0;
		    while (!isOnSyncQueue(node)) {
		    	//判断超时时间是否用完了
		        if (nanosTimeout <= 0L) {
		        	//如果已超时就需要执行取消条件等待操作
		            transferAfterCancelledWait(node);
		            break;
		        }
		        //将当前线程挂起一段时间, 线程在这期间可能被唤醒, 也可能自己醒来
		        LockSupport.parkNanos(this, nanosTimeout);
		        //线程醒来后先检查中断信息
		        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
		        	break;
		        }
		        long now = System.nanoTime();
		        //超时时间每次减去条件等待的时间
		        nanosTimeout -= now - lastTime;
		        lastTime = now;
		    }
		    //线程醒来后就会以独占模式获取锁
		    if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
		    	interruptMode = REINTERRUPT;
		    }
		    //由于transferAfterCancelledWait方法没有把nextWaiter置空, 所有这里要再清理一遍
		    if (node.nextWaiter != null) {
		    	unlinkCancelledWaiters();
		    }
		    //根据中断模式进行响应的中断处理
		    if (interruptMode != 0) {
		    	reportInterruptAfterWait(interruptMode);
		    }
		    //返回剩余时间
		    return nanosTimeout - (System.nanoTime() - lastTime);
		}
        
		//设置定时条件等待(相对时间), 进行自旋等待
		public final boolean await(long time, TimeUnit unit) throws InterruptedException {
		    if (unit == null) { throw new NullPointerException(); }
		    //获取超时时间的毫秒数
		    long nanosTimeout = unit.toNanos(time);
		    //如果线程被中断则抛出异常
		    if (Thread.interrupted()) { throw new InterruptedException(); }
		    //将当前线程添加条件队列尾部
		    Node node = addConditionWaiter();
		    //在进入条件等待之前先完全释放锁
		    int savedState = fullyRelease(node);
		    //获取当前时间的毫秒数
		    long lastTime = System.nanoTime();
		    boolean timedout = false;
		    int interruptMode = 0;
		    while (!isOnSyncQueue(node)) {
		    	//如果超时就需要执行取消条件等待操作
		        if (nanosTimeout <= 0L) {
		            timedout = transferAfterCancelledWait(node);
		            break;
		        }
		        //如果超时时间大于自旋时间, 就将线程挂起一段时间
		        if (nanosTimeout >= spinForTimeoutThreshold) {
		        	LockSupport.parkNanos(this, nanosTimeout);
		        }
		        //线程醒来后先检查中断信息
		        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
		        	break;
		        }
		        long now = System.nanoTime();
		        //超时时间每次减去条件等待的时间
		        nanosTimeout -= now - lastTime;
		        lastTime = now;
		    }
		    //线程醒来后就会以独占模式获取锁
		    if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
		    	interruptMode = REINTERRUPT;
		    }
		    //由于transferAfterCancelledWait方法没有把nextWaiter置空, 所有这里要再清理一遍
		    if (node.nextWaiter != null) {
		    	unlinkCancelledWaiters();
		    }
		    //根据中断模式进行响应的中断处理
		    if (interruptMode != 0) {
		    	reportInterruptAfterWait(interruptMode);
		    }
		    //返回是否超时标志
		    return !timedout;
		}

		//设置定时条件等待(绝对时间)
		public final boolean awaitUntil(Date deadline) throws InterruptedException {
		    if (deadline == null) { throw new NullPointerException(); } 
		    //获取绝对时间的毫秒数
		    long abstime = deadline.getTime();
		    //如果线程被中断则抛出异常
		    if (Thread.interrupted()) { throw new InterruptedException(); }
		    //将当前线程添加到条件队列尾部
		    Node node = addConditionWaiter();
		    //在进入条件等待之前先完全释放锁
		    int savedState = fullyRelease(node);
		    boolean timedout = false;
		    int interruptMode = 0;
		    while (!isOnSyncQueue(node)) {
		    	//如果超时就需要执行取消条件等待操作
		        if (System.currentTimeMillis() > abstime) {
		            timedout = transferAfterCancelledWait(node);
		            break;
		        }
		        //将线程挂起一段时间, 期间线程可能被唤醒, 也可能到了点自己醒来
		        LockSupport.parkUntil(this, abstime);
		        //线程醒来后先检查中断信息
		        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
		        	break;
		        }
		    }
		    //线程醒来后就会以独占模式获取锁
		    if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
		    	interruptMode = REINTERRUPT;
		    }
		    //由于transferAfterCancelledWait方法没有把nextWaiter置空, 所有这里要再清理一遍
		    if (node.nextWaiter != null) {
		    	unlinkCancelledWaiters();
		    }
		    //根据中断模式进行响应的中断处理
		    if (interruptMode != 0) {
		    	reportInterruptAfterWait(interruptMode);
		    }
		    //返回是否超时标志
		    return !timedout;
		}

        /********************************************其他支持操作*******************************************/
        
        //判断给定的对象是否是AbstractQueuedSynchronizer实例
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        //查询是否有线程在条件队列等待
        protected final boolean hasWaiters() {
            if (!isHeldExclusively()) {
            	throw new IllegalMonitorStateException();
            }
            //从头到尾遍历条件队列
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                	return true;
                }
            }
            return false;
        }

        //返回在条件队列中等待的线程数
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively()) {
            	throw new IllegalMonitorStateException();
            }
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                	++n;
                }
            }
            return n;
        }

        //返回在条件队列中等待的线程集合
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively()) {
            	throw new IllegalMonitorStateException();
            }
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null) {
                    	list.add(t);
                    }
                }
            }
            return list;
        }
    }

    /*****************************************************CAS操作的支持************************************************/
    
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));
        } catch (Exception ex) { throw new Error(ex); }
    }
    
    //以CAS方式设置头节点. 只在enq()方法使用
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    //以CAS方式设置尾节点. 只在enq()方法使用
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    //以CAS方式设置一个node的waitStatus
    private static final boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }
    
    //以CAS方式设置一个node的next
    private static final boolean compareAndSetNext(Node node, Node expect, Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
