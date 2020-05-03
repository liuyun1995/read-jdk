package jdk7.util.concurrent;

import java.util.concurrent.locks.Condition;
import jdk7.util.concurrent.locks.AbstractQueuedSynchronizer;
import jdk7.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

public class ThreadPoolExecutor extends AbstractExecutorService {
	
	//高3位表示线程池状态, 后29位表示线程个数
	private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
	private static final int COUNT_BITS = Integer.SIZE - 3;
	private static final int CAPACITY = (1 << COUNT_BITS) - 1;
	
	//运行状态  例:11100000000000000000000000000000
	private static final int RUNNING = -1 << COUNT_BITS;
	
	//关闭状态  例:00000000000000000000000000000000
	private static final int SHUTDOWN = 0 << COUNT_BITS;
	
	//停止状态  例:00100000000000000000000000000000
	private static final int STOP = 1 << COUNT_BITS;
	
	//整理状态  例:01000000000000000000000000000000
	private static final int TIDYING = 2 << COUNT_BITS;
	
	//终止状态  例:01100000000000000000000000000000
	private static final int TERMINATED = 3 << COUNT_BITS;
	
	private static int runStateOf(int c) { return c & ~CAPACITY; }
	private static int workerCountOf(int c) { return c & CAPACITY; }
	private static int ctlOf(int rs, int wc) { return rs | wc; }
    
    //c状态是否小于s状态
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }
    
    //c状态是否不小于s状态
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }
    
    //线程池是否为running状态
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }
    
    //将工作者数加一(CAS操作)
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }
    
    //将工作者数减一(CAS操作)
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }
    
    //将工作线程减至0
    private void decrementWorkerCount() {
        do {} while (!compareAndDecrementWorkerCount(ctl.get()));
    }
    
    //主线程锁
    private final ReentrantLock mainLock = new ReentrantLock();
    
    //条件队列
    private final Condition termination = mainLock.newCondition();
    
	//任务队列
	private final BlockingQueue<Runnable> workQueue;
	
	//工作者集合
	private final HashSet<Worker> workers = new HashSet<Worker>();
	
	//线程达到的最大值
	private int largestPoolSize;
	
	//已完成任务总数
	private long completedTaskCount;
	
	//线程工厂
	private volatile ThreadFactory threadFactory;
	
	//拒绝策略
	private volatile RejectedExecutionHandler handler;
	
	//闲置线程存活时间
	private volatile long keepAliveTime;
	
	//是否允许核心线程超时
	private volatile boolean allowCoreThreadTimeOut;
	
	//核心线程数量
	private volatile int corePoolSize;
	
	//最大线程数量
	private volatile int maximumPoolSize;
	
	//默认拒绝策略
	private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();
	
    //关闭权限许可证
    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");
    
    //工作者类
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
        //关联线程
        final Thread thread;
        //初始任务
        Runnable firstTask;
        //完成任务数
        volatile long completedTasks;

        //构造器
        Worker(Runnable firstTask) {
            //抑制中断直到runWorker
            setState(-1);
            //设置初始任务
            this.firstTask = firstTask;
            //设置关联线程
            this.thread = getThreadFactory().newThread(this);
        }

        public void run() {
            runWorker(this);
        }

        //判断是否占有锁, 0代表未占用, 1代表已占用
        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        //尝试获取锁
        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        //尝试释放锁
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock() { acquire(1); }
        public boolean tryLock() { return tryAcquire(1); }
        public void unlock() { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        //中断关联线程
        void interruptIfStarted() {
            Thread t;
            //将活动线程和闲置线程都中断
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                    //ignore
                }
            }
        }
    }

    /********************************************设置线程池状态*********************************************/

    //改变线程池状态
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) || ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c)))) {
            	break;
            }
        }
    }

    //尝试终止线程池
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            //以下两种情况终止线程池,其他情况直接返回:
            //1.状态为stop
            //2.状态为shutdown且任务队列为空
            if (isRunning(c) || runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty())) {
                return;
            }
            //若线程不为空则中断一个闲置线程后直接返回
            if (workerCountOf(c) != 0) {
                interruptIdleWorkers(ONLY_ONE);
                return;
            }
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                //将状态设置为tidying
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        //线程池终止后做的事情
                        terminated();
                    } finally {
                        //将状态设置为终止状态(TERMINATED)
                        ctl.set(ctlOf(TERMINATED, 0));
                        //唤醒条件队列所有线程
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            //若状态更改失败则再重试
        }
    }

    /********************************************中断工作线程*********************************************/
    
    //检查是否有关闭权限
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
            	//遍历工作者集合检查每个线程的权限
                for (Worker w : workers) {
                	security.checkAccess(w.thread);
                }
            } finally {
                mainLock.unlock();
            }
        }
    }
    
    //中断所有线程
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
        	//遍历集合中断所有线程
            for (Worker w : workers) {
            	w.interruptIfStarted();
            }
        } finally {
            mainLock.unlock();
        }
    }

    //中断闲置线程
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
        	//遍历集合中的所有线程
            for (Worker w : workers) {
                Thread t = w.thread;
                //若线程未被中断则去获取锁(未获取锁的为闲置线程)
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                //遍历下一个前判断是否只中断一个线程
                if (onlyOne) {
                	break;
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    //中断闲置线程
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;
    
    /********************************************其他公用设施*******************************************/

    //调用拒绝处理程序执行给定的任务
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    void onShutdown() {}

    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    //删除任务队列所有任务
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        List<Runnable> taskList = new ArrayList<Runnable>();
        //将任务队列转移到taskList
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r)) {
                	taskList.add(r);
                }
            }
        }
        return taskList;
    }

    /****************************************添加,运行和清理Worker******************************************/

    //添加工作线程
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
            //只有以下两种情况会继续添加线程
            //1.状态为running
            //2.状态为shutdown，首要任务为空，但任务队列中还有任务
            if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                return false;
            }
            for (;;) {
                int wc = workerCountOf(c);
                //以下三种情况不继续添加线程:
                //1.线程数大于线程池总容量
                //2.当前线程为核心线程，且核心线程数达到corePoolSize
                //3.当前线程非核心线程，且总线程达到maximumPoolSize
                if (wc >= CAPACITY || wc >= (core ? corePoolSize : maximumPoolSize)) {
                    return false;
                }
                //否则继续添加线程, 先将线程数加一
                if (compareAndIncrementWorkerCount(c)) {
                    //执行成功则跳过外循环
                    break retry;
                }
                //CAS操作失败再次检查线程池状态
                c = ctl.get();
                //如果线程池状态改变则继续执行外循环
                if (runStateOf(c) != rs) {
                    continue retry;
                }
                //否则表明CAS操作失败是workerCount改变, 继续执行内循环
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            final ReentrantLock mainLock = this.mainLock;
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                mainLock.lock();
                try {
                    int c = ctl.get();
                    int rs = runStateOf(c);
                    if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {
                        //如果线程已经开启则抛出异常
                        if (t.isAlive()) throw new IllegalThreadStateException();
                        //将工作者添加到集合中
                        workers.add(w);
                        int s = workers.size();
                        //记录线程达到的最大值
                        if (s > largestPoolSize) {
                            largestPoolSize = s;
                        }
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                //将工作者添加到集合后则启动线程
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            //如果线程启动失败则回滚操作
            if (!workerStarted) {
                addWorkerFailed(w);
            }
        }
        return workerStarted;
    }

    //添加工作者失败时的回滚操作
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
        	//将工作者从集合中移除
            if (w != null) {
            	workers.remove(w);
            }
            //将工作者数量减一
            decrementWorkerCount();
            //尝试终止线程池
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    //删除工作线程
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        //若非正常完成则将线程数减为0
        if (completedAbruptly) {
            decrementWorkerCount();
        }
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //统计完成的任务总数
            completedTaskCount += w.completedTasks;
            //在这将工作线程移除
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        //尝试终止线程池
        tryTerminate();
        //再次检查线程池状态
        int c = ctl.get();
        //若状态为running或shutdown, 则将线程数恢复到最小值
        if (runStateLessThan(c, STOP)) {
            //线程正常完成任务被移除
            if (!completedAbruptly) {
                //允许核心线程超时最小值为0, 否则最小值为核心线程数
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                //如果任务队列还有任务, 则保证至少有一个线程
                if (min == 0 && !workQueue.isEmpty()) {
                    min = 1;
                }
                //若线程数大于最小值则不新增了
                if (workerCountOf(c) >= min) {
                    return;
                }
            }
            //新增工作线程
            addWorker(null, false);
        }
    }

    //从任务队列中获取任务
    private Runnable getTask() {
        //上一次获取任务是否超时
        boolean timedOut = false;
        retry:
        //在for循环里自旋
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
            //以下两种情况会将工作者数减为0并返回null，并直接使该线程终止:
            //1.状态为shutdown并且任务队列为空
            //2.状态为stop, tidying或terminated
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            boolean timed;
            //判断是否要剔除当前线程
            for (;;) {
                int wc = workerCountOf(c);
                //以下两种情况会在限定时间获取任务:
                //1.允许核心线程超时
                //2.线程数大于corePoolSize
                timed = allowCoreThreadTimeOut || wc > corePoolSize;
                //以下两种情况不执行剔除操作：
                //1.上次任务获取未超时
                //2.上次任务获取超时, 但没要求在限定时间获取
                if (wc <= maximumPoolSize && !(timedOut && timed)) {
                    break;
                }
                //若上次任务获取超时, 且规定在限定时间获取, 则将线程数减一
                if (compareAndDecrementWorkerCount(c)) {
                    //CAS操作成功后直接返回null
                    return null;
                }
                //CAS操作失败后再次检查状态
                c = ctl.get();
                //若状态改变就从外层循环重试
                if (runStateOf(c) != rs) {
                    continue retry;
                }
                //否则表明是workerCount改变, 继续在内循环重试
            }

            try {
                //若timed为true, 则在规定时间内返回
                //若timed为false, 则阻塞直到获取成功
                //注意:闲置线程会一直在这阻塞
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                //获取任务不为空则返回该任务
                if (r != null) {
                    return r;
                }
                //否则将超时标志设为true
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    //运行工作者
    final void runWorker(Worker w) {
        //获取当前工作线程
        Thread wt = Thread.currentThread();
        //获取工作者的初始任务
        Runnable task = w.firstTask;
        //将工作者的初始任务置空
        w.firstTask = null;
        //将同步状态从-1设为0
        w.unlock();
        boolean completedAbruptly = true;
        try {
            //初始任务不为空则执行初始任务, 否则从队列获取任务
            while (task != null || (task = getTask()) != null) {
                //确保获取到任务后才加锁
                w.lock();
                //若状态大于等于stop, 保证当前线程被中断
                //若状态小于stop, 保证当前线程未被中断
                //在清理中断状态时可能有其他线程在修改, 所以会再检查一次
                if ((runStateAtLeast(ctl.get(), STOP) ||
                    (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted()) {
                    wt.interrupt();
                }
                try {
                    //任务执行前做些事情
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        //执行当前任务
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        //任务执行后做一些事情
                        afterExecute(task, thrown);
                    }
                } finally {
                    //将执行完的任务置空
                    task = null;
                    //将完成的任务数加一
                    w.completedTasks++;
                    w.unlock();
                }
            }
            //设置该线程为正常完成任务
            completedAbruptly = false;
        } finally {
            //执行完所有任务后将线程删除
            processWorkerExit(w, completedAbruptly);
        }
    }
    
    /****************************************公共构造器和方法********************************************/

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), defaultHandler);
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }
    
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), handler);
    }
    
	//核心构造器
	public ThreadPoolExecutor(int corePoolSize,
	                          int maximumPoolSize,
	                          long keepAliveTime,
	                          TimeUnit unit,
	                          BlockingQueue<Runnable> workQueue,
	                          ThreadFactory threadFactory,
	                          RejectedExecutionHandler handler) {
	    if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
	    	throw new IllegalArgumentException();
	    }    
	    if (workQueue == null || threadFactory == null || handler == null) {
	    	throw new NullPointerException();
	    }
	    this.corePoolSize = corePoolSize;                  //设置核心线程数量
	    this.maximumPoolSize = maximumPoolSize;            //设置最大线程数量
	    this.workQueue = workQueue;                        //设置任务队列
	    this.keepAliveTime = unit.toNanos(keepAliveTime);  //设置存活时间
	    this.threadFactory = threadFactory;                //设置线程工厂
	    this.handler = handler;                            //设置拒绝策略
	}
    
	//核心执行方法
	public void execute(Runnable command) {
	    if (command == null) throw new NullPointerException();
	    int c = ctl.get();
	    //若线程数小于corePoolSize则新建核心线程执行任务
	    if (workerCountOf(c) < corePoolSize) {
	        if (addWorker(command, true)) return;
	        c = ctl.get();
	    }
	    //否则将该任务放入到任务队列
	    if (isRunning(c) && workQueue.offer(command)) {
	        int recheck = ctl.get();
	        //再次检查是否为Running状态, 若不是则将该任务从队列中移除
	        if (!isRunning(recheck) && remove(command)) {
	            //移除成功之后再执行拒绝策略
	        	reject(command);
	        //这里判断主要是
	        }else if (workerCountOf(recheck) == 0) {
	        	addWorker(null, false);
	        }
	    //若任务队列已满则新建非核心线程执行该任务
	    }else if (!addWorker(command, false)) {
	    	//新增失败则执行拒绝策略
	    	reject(command);
	    }
	}
    
    //平缓关闭线程池
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //检查是否有关闭的权限
            checkShutdownAccess();
            //将线程池状态设为shutdown
            advanceRunState(SHUTDOWN);
            //中断闲置的线程
            interruptIdleWorkers();
            //对外提供的钩子
            onShutdown();
        } finally {
            mainLock.unlock();
        }
        //尝试终止线程池
        tryTerminate();
    }

    //立刻关闭线程池
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //检查是否有关闭的权限
            checkShutdownAccess();
            //将线程池状态设为stop
            advanceRunState(STOP);
            //中断所有工作线程
            interruptWorkers();
            //排干任务队列
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        //尝试终止线程池
        tryTerminate();
        return tasks;
    }

    //判断是否要关闭
    public boolean isShutdown() {
        return !isRunning(ctl.get());
    }
    
    //是否正在终止线程池
    public boolean isTerminating() {
        int c = ctl.get();
        return !isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    //是否已经终止线程池
    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    //阻塞当前线程等待线程池终止
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
            	//若线程池已终止则不需要阻塞了
                if (runStateAtLeast(ctl.get(), TERMINATED)) {
                	return true;
                }
                if (nanos <= 0) {
                	return false;
                }
                //否则将当前线程放入条件队列等待
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }
    
    //销毁线程池
    protected void finalize() {
        shutdown();
    }
    
    //设置线程工厂
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null) throw new NullPointerException();
        this.threadFactory = threadFactory;
    }
    
    //获取线程工厂
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }
    
    //设置拒绝策略
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null) throw new NullPointerException();
        this.handler = handler;
    }
    
    //获取拒绝策略
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    //设置核心线程大小
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0) throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        //若线程数大于核心线程则中断闲置线程
        if (workerCountOf(ctl.get()) > corePoolSize) {
        	interruptIdleWorkers();
        }else if (delta > 0) {
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) break;
            }
        }
    }

    //获取核心线程数
    public int getCorePoolSize() {
        return corePoolSize;
    }

    //预先启动一个核心线程
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize && addWorker(null, true);
    }

    //预先启动一个线程
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize) {
        	addWorker(null, true);
        }else if (wc == 0) {
        	addWorker(null, false);
        }
    }

    //预先启动所有核心线程
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true)) {
        	++n;
        }
        return n;
    }
    
    //是否允许核心线程等待超时
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    //设置是否允许核心线程超时
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0) {
        	throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value) {
            	interruptIdleWorkers();
            }
        }
    }
    
    //设置最大允许的线程数
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) {
        	throw new IllegalArgumentException();
        }
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize) {
        	interruptIdleWorkers();
        }
    }

    //获取最大允许的线程数
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    //设置闲置线程存活时间
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0) {
        	throw new IllegalArgumentException();
        }
        if (time == 0 && allowsCoreThreadTimeOut()) {
        	throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0) {
        	interruptIdleWorkers();
        }
    }

    //获取闲置线程存活时间
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }
     
    /****************************************用户级队列操作********************************************/
    
    //获取任务队列
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    //移除指定任务
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        //尝试终止线程池
        tryTerminate();
        return removed;
    }
    
    //移除已取消的任务
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled()) {
                	it.remove();
                }
            }
        } catch (ConcurrentModificationException fallThrough) {
            for (Object r : q.toArray()) {
            	if (r instanceof Future<?> && ((Future<?>)r).isCancelled()) {
            		q.remove(r);
            	}
            }
        }
        //尝试终止线程池
        tryTerminate();
    }
    
    //返回线程池中当前的线程数
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //线程池状态为tidying或terminated则返回0, 否则返回工作者集合的大小
            return runStateAtLeast(ctl.get(), TIDYING) ? 0 : workers.size();
        } finally {
            mainLock.unlock();
        }
    }
    
    //返回正执行任务的线程数
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            //若已锁定则表明正在执行任务
            for (Worker w : workers) {
            	if (w.isLocked()) ++n;
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }
    
    //返回最大达到的线程数
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }
    
    //返回已执行的任务总数
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked()) ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }
    
    //返回已完成的任务总数
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
            	n += w.completedTasks;
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }
    
    /****************************************扩展钩子方法********************************************/
    
    //任务执行前要做的事情
    protected void beforeExecute(Thread t, Runnable r) {}

    //任务执行后要做的事情
    protected void afterExecute(Runnable r, Throwable t) {}

    //线程池终止后要做的事情
    protected void terminated() {}

    /***************************************预定义的拒绝策略 ******************************************/

    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        public CallerRunsPolicy() {}
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    public static class AbortPolicy implements RejectedExecutionHandler {
        public AbortPolicy() {}
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        }
    }

    public static class DiscardPolicy implements RejectedExecutionHandler {
        public DiscardPolicy() {}
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {}
    }

    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        public DiscardOldestPolicy() {}
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
    
}

