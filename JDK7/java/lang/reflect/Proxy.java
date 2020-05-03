package jdk7.lang.reflect;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ReflectPermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import sun.misc.ProxyGenerator;
import sun.misc.VM;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import sun.reflect.misc.ReflectUtil;
import sun.security.util.SecurityConstants;

public class Proxy implements java.io.Serializable {

    private static final long serialVersionUID = -2222568056686623797L;

    //代理类构造器的参数类型, 将InvocationHandler传入代理类的构造器
    private static final Class<?>[] constructorParams = { InvocationHandler.class };

    //动态代理类的缓存, 这里的keyFactory用于生成二级缓存key, ProxyClassFactory用于生成代理类
    private static final WeakCache<ClassLoader, Class<?>[], Class<?>>
        proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());

    //InvocationHandler的引用
    protected InvocationHandler h;

    private Proxy() {}

    protected Proxy(InvocationHandler h) {
    	//利用JDK自带的工具类Objects判断不能为空
        Objects.requireNonNull(h);
        //构造Proxy时传入InvocationHandler的引用
        this.h = h;
    }

    //该方法返回一个代理类,该代理类由给定的类加载器进行加载并且将会实现所有提供的接口
    @CallerSensitive
    public static Class<?> getProxyClass(ClassLoader loader, Class<?>... interfaces)
    		throws IllegalArgumentException {
    	//复制接口数组
    	final Class<?>[] intfs = interfaces.clone();
    	//启动安全管理器
        final SecurityManager sm = System.getSecurityManager();
        //进行一些权限检验
        if (sm != null) {
            checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
        }
        //传入目标代理类的类加载器和实现的接口数组, 最后返回一个代理类
        return getProxyClass0(loader, intfs);
    }

    /*
     * Check permissions required to create a Proxy class.
     *
     * To define a proxy class, it performs the access checks as in
     * Class.forName (VM will invoke ClassLoader.checkPackageAccess):
     * 1. "getClassLoader" permission check if loader == null
     * 2. checkPackageAccess on the interfaces it implements
     *
     * To get a constructor and new instance of a proxy class, it performs
     * the package access check on the interfaces it implements
     * as in Class.getConstructor.
     *
     * If an interface is non-public, the proxy class must be defined by
     * the defining loader of the interface.  If the caller's class loader
     * is not the same as the defining loader of the interface, the VM
     * will throw IllegalAccessError when the generated proxy class is
     * being defined via the defineClass0 method.
     */
    private static void checkProxyAccess(Class<?> caller,
                                         ClassLoader loader,
                                         Class<?>... interfaces)
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader ccl = caller.getClassLoader();
            if (VM.isSystemDomainLoader(loader) && !VM.isSystemDomainLoader(ccl)) {
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
            }
            ReflectUtil.checkProxyPackageAccess(ccl, interfaces);
        }
    }

    //生成一个代理类, 在调用之前必须调用checkProxyAccess方法进行权限检查
	private static Class<?> getProxyClass0(ClassLoader loader,
	                                       Class<?>... interfaces) {
		//目标类实现的接口不能大于65535
	    if (interfaces.length > 65535) {
	        throw new IllegalArgumentException("interface limit exceeded");
	    }
	    //获取代理类使用了缓存机制
	    return proxyClassCache.get(loader, interfaces);
	}

    
    //实现了零个接口的代理类的key
    private static final Object key0 = new Object();

    //实现了一个接口的代理类的key
    private static final class Key1 extends WeakReference<Class<?>> {
        private final int hash;

        Key1(Class<?> intf) {
            super(intf);
            this.hash = intf.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            Class<?> intf;
            return this == obj ||
                   obj != null &&
                   obj.getClass() == Key1.class &&
                   (intf = get()) != null &&
                   intf == ((Key1) obj).get();
        }
    }

    //实现了两个接口的代理类的key
    private static final class Key2 extends WeakReference<Class<?>> {
        private final int hash;
        private final WeakReference<Class<?>> ref2;

        Key2(Class<?> intf1, Class<?> intf2) {
            super(intf1);
            hash = 31 * intf1.hashCode() + intf2.hashCode();
            ref2 = new WeakReference<Class<?>>(intf2);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            Class<?> intf1, intf2;
            return this == obj ||
                   obj != null &&
                   obj.getClass() == Key2.class &&
                   (intf1 = get()) != null &&
                   intf1 == ((Key2) obj).get() &&
                   (intf2 = ref2.get()) != null &&
                   intf2 == ((Key2) obj).ref2.get();
        }
    }

    //实现了3个或以上接口的代理类的key
    private static final class KeyX {
        private final int hash;
        private final WeakReference<Class<?>>[] refs;

        @SuppressWarnings("unchecked")
        KeyX(Class<?>[] interfaces) {
        	//KeyX的哈希码是根据接口数组生成的
            hash = Arrays.hashCode(interfaces);
            //根据代理类实现的接口数去新建一个弱引用的数组
            refs = (WeakReference<Class<?>>[])new WeakReference<?>[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
            	//依次用弱引用包装代理类实现的接口Class
                refs[i] = new WeakReference<>(interfaces[i]);
            }
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj ||
                   obj != null &&
                   obj.getClass() == KeyX.class &&
                   equals(refs, ((KeyX) obj).refs);
        }

        private static boolean equals(WeakReference<Class<?>>[] refs1,
                                      WeakReference<Class<?>>[] refs2) {
            if (refs1.length != refs2.length) {
                return false;
            }
            for (int i = 0; i < refs1.length; i++) {
                Class<?> intf = refs1[i].get();
                if (intf == null || intf != refs2[i].get()) {
                    return false;
                }
            }
            return true;
        }
    }

    //KeyFactory用来生成二级缓存的key
    private static final class KeyFactory implements BiFunction<ClassLoader, Class<?>[], Object> {
        @Override
        public Object apply(ClassLoader classLoader, Class<?>[] interfaces) {
            switch (interfaces.length) {
                case 1: return new Key1(interfaces[0]); // the most frequent
                case 2: return new Key2(interfaces[0], interfaces[1]);
                case 0: return key0;
                default: return new KeyX(interfaces);
            }
        }
    }

    //代理类生成工厂
    private static final class ProxyClassFactory implements BiFunction<ClassLoader, Class<?>[], Class<?>> {
        //代理类名称前缀
        private static final String proxyClassNamePrefix = "$Proxy";
        //用原子类来生成代理类的序号, 以此来确定唯一的代理类
        private static final AtomicLong nextUniqueNumber = new AtomicLong();
        @Override
        public Class<?> apply(ClassLoader loader, Class<?>[] interfaces) {
            Map<Class<?>, Boolean> interfaceSet = new IdentityHashMap<>(interfaces.length);
            for (Class<?> intf : interfaces) {
            	//用类加载器去验证该接口是否是相同的对象
            	//同一个类用不同的类加载器去加载是不相等的
                Class<?> interfaceClass = null;
                try {
                	//用指定的类加载器并通过接口的全限定名去加载接口的类对象
                    interfaceClass = Class.forName(intf.getName(), false, loader);
                } catch (ClassNotFoundException e) {
                }
                //如果接口不是由指定类加载器加载的则抛出异常
                if (interfaceClass != intf) {
                    throw new IllegalArgumentException(
                        intf + " is not visible from class loader");
                }
                //如果不是一个接口则抛出异常
                if (!interfaceClass.isInterface()) {
                    throw new IllegalArgumentException(
                        interfaceClass.getName() + " is not an interface");
                }
                //如果接口重复了就抛出异常
                if (interfaceSet.put(interfaceClass, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException(
                        "repeated interface: " + interfaceClass.getName());
                }
            }
            //生成代理类的包名
            String proxyPkg = null;
            //生成代理类的访问标志, 默认是public final的
            int accessFlags = Modifier.PUBLIC | Modifier.FINAL;
            for (Class<?> intf : interfaces) {
            	//获取接口的访问标志
                int flags = intf.getModifiers();
                //如果接口的访问标志不是public, 那么生成代理类的包名和接口包名相同
                if (!Modifier.isPublic(flags)) {
                	//生成的代理类的访问标志设置为final
                    accessFlags = Modifier.FINAL;
                    //获取接口全限定名, 例如：java.util.Collection
                    String name = intf.getName();
                    int n = name.lastIndexOf('.');
                    //剪裁后得到包名:java.util
                    String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
                    //生成的代理类的包名和接口包名是一样的
                    if (proxyPkg == null) {
                        proxyPkg = pkg;
                    } else if (!pkg.equals(proxyPkg)) {
                    	//代理类如果实现不同包的接口, 并且接口都不是public的, 那么就会在这里报错
                        throw new IllegalArgumentException(
                            "non-public interfaces from different packages");
                    }
                }
            }
            //如果接口访问标志都是public的话, 那生成的代理类都放到默认的包下：com.sun.proxy
            if (proxyPkg == null) {
                proxyPkg = ReflectUtil.PROXY_PACKAGE + ".";
            }
            //获取代理类的序号
            long num = nextUniqueNumber.getAndIncrement();
            //生成代理类的全限定名, 包名+前缀+序号, 例如：com.sun.proxy.$Proxy0
            String proxyName = proxyPkg + proxyClassNamePrefix + num;
            //这里是核心, 用ProxyGenerator来生成字节码, 该类放在sun.misc包下
            byte[] proxyClassFile = ProxyGenerator.generateProxyClass(proxyName, interfaces, accessFlags);
            try {
            	//根据二进制文件生成相应的Class实例
                return defineClass0(loader, proxyName, proxyClassFile, 0, proxyClassFile.length);
            } catch (ClassFormatError e) {
                throw new IllegalArgumentException(e.toString());
            }
        }
    }

    
	@CallerSensitive
	public static Object newProxyInstance(ClassLoader loader,
	                                      Class<?>[] interfaces,
	                                      InvocationHandler h) throws IllegalArgumentException {
	    //验证传入的InvocationHandler不能为空
		Objects.requireNonNull(h);
		//复制代理类实现的所有接口
	    final Class<?>[] intfs = interfaces.clone();
	    //获取安全管理器
	    final SecurityManager sm = System.getSecurityManager();
	    //进行一些权限检验
	    if (sm != null) {
	        checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
	    }
	    //该方法先从缓存获取代理类, 如果没有再去生成一个代理类
	    Class<?> cl = getProxyClass0(loader, intfs);
	    try {
	    	//进行一些权限检验
	        if (sm != null) {
	            checkNewProxyPermission(Reflection.getCallerClass(), cl);
	        }
	        //获取参数类型是InvocationHandler.class的代理类构造器
	        final Constructor<?> cons = cl.getConstructor(constructorParams);
	        final InvocationHandler ih = h;
	        //代理类是不可访问的, 就使用特权将它的构造器设置为可访问
	        if (!Modifier.isPublic(cl.getModifiers())) {
	            AccessController.doPrivileged(new PrivilegedAction<Void>() {
	                public Void run() {
	                    cons.setAccessible(true);
	                    return null;
	                }
	            });
	        }
	        //传入InvocationHandler实例去构造一个代理类的实例
	        //所有代理类都继承自Proxy, 因此这里会调用Proxy的构造器将InvocationHandler引用传入
	        return cons.newInstance(new Object[]{h});
	    } catch (Exception e) {
	    	//为了节省篇幅, 笔者统一用Exception捕获了所有异常
	        throw new InternalError(e.toString(), e);
	    }
	}
    
    

    private static void checkNewProxyPermission(Class<?> caller, Class<?> proxyClass) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (ReflectUtil.isNonPublicProxyClass(proxyClass)) {
                ClassLoader ccl = caller.getClassLoader();
                ClassLoader pcl = proxyClass.getClassLoader();

                // do permission check if the caller is in a different runtime package
                // of the proxy class
                int n = proxyClass.getName().lastIndexOf('.');
                String pkg = (n == -1) ? "" : proxyClass.getName().substring(0, n);

                n = caller.getName().lastIndexOf('.');
                String callerPkg = (n == -1) ? "" : caller.getName().substring(0, n);

                if (pcl != ccl || !pkg.equals(callerPkg)) {
                    sm.checkPermission(new ReflectPermission("newProxyInPackage." + pkg));
                }
            }
        }
    }

    /**
     * Returns true if and only if the specified class was dynamically
     * generated to be a proxy class using the {@code getProxyClass}
     * method or the {@code newProxyInstance} method.
     *
     * <p>The reliability of this method is important for the ability
     * to use it to make security decisions, so its implementation should
     * not just test if the class in question extends {@code Proxy}.
     *
     * @param   cl the class to test
     * @return  {@code true} if the class is a proxy class and
     *          {@code false} otherwise
     * @throws  NullPointerException if {@code cl} is {@code null}
     */
    public static boolean isProxyClass(Class<?> cl) {
        return Proxy.class.isAssignableFrom(cl) && proxyClassCache.containsValue(cl);
    }

    /**
     * Returns the invocation handler for the specified proxy instance.
     *
     * @param   proxy the proxy instance to return the invocation handler for
     * @return  the invocation handler for the proxy instance
     * @throws  IllegalArgumentException if the argument is not a
     *          proxy instance
     * @throws  SecurityException if a security manager, <em>s</em>, is present
     *          and the caller's class loader is not the same as or an
     *          ancestor of the class loader for the invocation handler
     *          and invocation of {@link SecurityManager#checkPackageAccess
     *          s.checkPackageAccess()} denies access to the invocation
     *          handler's class.
     */
    @CallerSensitive
    public static InvocationHandler getInvocationHandler(Object proxy)
        throws IllegalArgumentException
    {
        /*
         * Verify that the object is actually a proxy instance.
         */
        if (!isProxyClass(proxy.getClass())) {
            throw new IllegalArgumentException("not a proxy instance");
        }

        final Proxy p = (Proxy) proxy;
        final InvocationHandler ih = p.h;
        if (System.getSecurityManager() != null) {
            Class<?> ihClass = ih.getClass();
            Class<?> caller = Reflection.getCallerClass();
            if (ReflectUtil.needsPackageAccessCheck(caller.getClassLoader(),
                                                    ihClass.getClassLoader()))
            {
                ReflectUtil.checkPackageAccess(ihClass);
            }
        }

        return ih;
    }

    private static native Class<?> defineClass0(ClassLoader loader, String name,
                                                byte[] b, int off, int len);
}

