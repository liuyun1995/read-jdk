package jdk7.util.collection;

import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class LinkedList<E> extends AbstractSequentialList<E> implements List<E>, Deque<E>, Cloneable, java.io.Serializable {
    
	//集合元素个数
	transient int size = 0;
	
	//头结点引用
	transient Node<E> first;
	
	//尾节点引用
	transient Node<E> last;
	
	//无参构造器
	public LinkedList() {}
	
	//传入外部集合的构造器
	public LinkedList(Collection<? extends E> c) {
	    this();
	    addAll(c);
	}

    //添加结点到链表头部
    private void linkFirst(E e) {
    	//获取头结点引用
        final Node<E> f = first;
        //创建新结点, 新结点的下一个结点引用指向头结点
        final Node<E> newNode = new Node<>(null, e, f);
        //将头结点引用指向新结点
        first = newNode;
        //如果头结点引用指向的结点为空, 表明当前链表没有结点
        if (f == null) {
        	//将尾结点引用指向新结点
        	last = newNode;
        }else {
        	//将头结点的上一个结点的引用指向新结点
        	f.prev = newNode;
        }
        //集合元素个数加一
        size++;
        //修改次数加一
        modCount++;
    }

    //添加结点到链表末尾
    void linkLast(E e) {
    	//获取尾结点引用
        final Node<E> l = last;
        //创建新结点, 新结点的上一个结点的引用指向尾结点
        final Node<E> newNode = new Node<>(l, e, null);
        //将尾结点引用指向新结点
        last = newNode;
        //如果尾结点引用指向的结点为空, 表明当前链表没有结点
        if (l == null) {
        	//将头结点引用指向新结点
        	first = newNode;
        } else {
        	//将尾结点的下一个结点的引用指向新结点
        	l.next = newNode;
        }
        //集合元素个数加一
        size++;
        //修改次数加一
        modCount++;
    }

	//链接到指定结点之前
	void linkBefore(E e, Node<E> succ) {
	    //获取给定结点的上一个结点引用
	    final Node<E> pred = succ.prev;
	    //创建新结点, 新结点的上一个结点引用指向给定结点的上一个结点
	    //新结点的下一个结点的引用指向给定的结点
	    final Node<E> newNode = new Node<>(pred, e, succ);
	    //将给定结点的上一个结点引用指向新结点
	    succ.prev = newNode;
	    //如果给定结点的上一个结点为空, 表明给定结点为头结点
	    if (pred == null) {
	    	//将头结点引用指向新结点
	    	first = newNode;
	    } else {
	    	//否则, 将给定结点的上一个结点的下一个结点引用指向新结点
	    	pred.next = newNode;
	    }
	    //集合元素个数加一
	    size++;
	    //修改次数加一
	    modCount++;
	}

    //卸载头结点
    private E unlinkFirst(Node<E> f) {
    	//获取给定结点的元素
        final E element = f.item;
        //获取给定结点的下一个结点的引用
        final Node<E> next = f.next;
        //将给定结点的元素置空
        f.item = null;
        //将给定结点的下一个结点引用置空
        f.next = null;
        //将头结点引用指向给定结点的下一个结点
        first = next;
        //如果给定结点的下一个结点为空, 表明当前链表只有一个元素
        if (next == null) {
        	//将尾结点引用置空, 为了帮助垃圾回收
            last = null;
        } else {
        	//否则, 将给定结点的下一个结点的上一个结点引用置空
        	next.prev = null;
        }
        //集合元素个数减一
        size--;
        //修改次数减一
        modCount++;
        return element;
    }

    //卸载尾节点
    private E unlinkLast(Node<E> l) {
        //获取给定结点的元素
        final E element = l.item;
        //获取给定结点的上一个结点的引用
        final Node<E> prev = l.prev;
        //将给定结点的元素置空
        l.item = null;
        //将给定结点的上一个结点引用置空
        l.prev = null;
        //将尾节点引用指向给定结点的上一个结点
        last = prev;
        //如果给定结点的上一个结点为空, 表明当前链表只有一个结点
        if (prev == null) {
        	//将头结点引用置空, 帮助垃圾回收
        	first = null;
        } else {
        	//
        	prev.next = null;
        }
        size--;
        modCount++;
        return element;
    }

	//卸载指定结点
	E unlink(Node<E> x) {
	    //获取给定结点的元素
	    final E element = x.item;
	    //获取给定结点的下一个结点的引用
	    final Node<E> next = x.next;
	    //获取给定结点的上一个结点的引用
	    final Node<E> prev = x.prev;
	
	    //如果给定结点的上一个结点为空, 说明给定结点为头结点
	    if (prev == null) {
	    	//将头结点引用指向给定结点的下一个结点
	        first = next;
	    } else {
	    	//将上一个结点的后继结点引用指向给定结点的后继结点
	        prev.next = next;
	        //将给定结点的上一个结点置空
	        x.prev = null;
	    }
	
	    //如果给定结点的下一个结点为空, 说明给定结点为尾结点
	    if (next == null) {
	    	//将尾结点引用指向给定结点的上一个结点
	        last = prev;
	    } else {
	    	//将下一个结点的前继结点引用指向给定结点的前继结点
	        next.prev = prev;
	        x.next = null;
	    }
	
	    //将给定结点的元素置空
	    x.item = null;
	    //集合元素个数减一
	    size--;
	    //修改次数加一
	    modCount++;
	    return element;
	}

    //获取头结点
    public E getFirst() {
        final Node<E> f = first;
        if (f == null) {
        	throw new NoSuchElementException();
        }
        return f.item;
    }

    //获取尾节点
    public E getLast() {
        final Node<E> l = last;
        if (l == null) {
        	throw new NoSuchElementException();
        }
        return l.item;
    }

    //移除头结点
    public E removeFirst() {
        final Node<E> f = first;
        if (f == null) {
        	throw new NoSuchElementException();
        }
        return unlinkFirst(f);
    }

    //移除尾节点
    public E removeLast() {
        final Node<E> l = last;
        if (l == null) {
        	throw new NoSuchElementException();
        }
        return unlinkLast(l);
    }

    //添加头结点
    public void addFirst(E e) {
        linkFirst(e);
    }

    //添加尾节点
    public void addLast(E e) {
        linkLast(e);
    }

    //判断链表是否包含指定元素
    public boolean contains(Object o) {
    	//定位指定元素在链表中的位置
        return indexOf(o) != -1;
    }

    //返回集合元素个数
    public int size() {
        return size;
    }

	//增(添加)
	public boolean add(E e) {
		//在链表尾部添加
	    linkLast(e);
	    return true;
	}
	
	//增(插入)
	public void add(int index, E element) {
	    checkPositionIndex(index);
	    if (index == size) {
	    	//在链表尾部添加
	    	linkLast(element);
	    } else {
	    	//在链表中部插入
	    	linkBefore(element, node(index));
	    }
	}
	
	//删(给定下标)
	public E remove(int index) {
		//检查下标是否合法
	    checkElementIndex(index);
	    return unlink(node(index));
	}
	
	//删(给定元素)
	public boolean remove(Object o) {
	    if (o == null) {
	        for (Node<E> x = first; x != null; x = x.next) {
	            if (x.item == null) {
	                unlink(x);
	                return true;
	            }
	        }
	    } else {
	    	//遍历链表
	        for (Node<E> x = first; x != null; x = x.next) {
	            if (o.equals(x.item)) {
	            	//找到了就删除
	                unlink(x);
	                return true;
	            }
	        }
	    }
	    return false;
	}
	
	//改
	public E set(int index, E element) {
		//检查下标是否合法
	    checkElementIndex(index);
	    //获取指定下标的结点引用
	    Node<E> x = node(index);
	    //获取指定下标结点的值
	    E oldVal = x.item;
	    //将结点元素设置为新的值
	    x.item = element;
	    //返回之前的值
	    return oldVal;
	}
	
	//查
	public E get(int index) {
		//检查下标是否合法
	    checkElementIndex(index);
	    //返回指定下标的结点的值
	    return node(index).item;
	}
    
	//根据指定位置获取结点
	Node<E> node(int index) {
		//如果下标在链表前半部分, 就从头开始查起
	    if (index < (size >> 1)) {
	        Node<E> x = first;
	        for (int i = 0; i < index; i++) {
	        	x = x.next;
	        }
	        return x;
	    } else {
	    	//如果下标在链表后半部分, 就从尾开始查起
	        Node<E> x = last;
	        for (int i = size - 1; i > index; i--) {
	        	x = x.prev;
	        }
	        return x;
	    }
	}
    

    //添加一个集合
    public boolean addAll(Collection<? extends E> c) {
    	//在链表尾部添加集合
        return addAll(size, c);
    }

    //在指定位置插入集合
    public boolean addAll(int index, Collection<? extends E> c) {
    	//检查位置是否合法
        checkPositionIndex(index);

        Object[] a = c.toArray();
        int numNew = a.length;
        if (numNew == 0) {
        	return false;
        }
        Node<E> pred, succ;
        if (index == size) {
            succ = null;
            pred = last;
        } else {
            succ = node(index);
            pred = succ.prev;
        }

        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)
                first = newNode;
            else
                pred.next = newNode;
            pred = newNode;
        }

        if (succ == null) {
            last = pred;
        } else {
            pred.next = succ;
            succ.prev = pred;
        }

        size += numNew;
        modCount++;
        return true;
    }

    //清空集合
    public void clear() {
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
        modCount++;
    }

    /**
     * Tells if the argument is the index of an existing element.
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    /**
     * Tells if the argument is the index of a valid position for an
     * iterator or an add operation.
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    //越界消息
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    private void checkElementIndex(int index) {
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    //返回指定元素第一次出现的位置
    public int indexOf(Object o) {
        int index = 0;
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                	return index;
                }
                index++;
            }
        } else {
        	//从头部遍历
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                	return index;
                }
                index++;
            }
        }
        return -1;
    }

    //返回指定元素最后出现的位置
    public int lastIndexOf(Object o) {
        int index = size;
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (x.item == null) {
                	return index;
                }
            }
        } else {
        	//从尾部遍历
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (o.equals(x.item)) {
                	return index;
                }
            }
        }
        return -1;
    }

	//单向队列操作
	//获取头结点
	public E peek() {
	    final Node<E> f = first;
	    return (f == null) ? null : f.item;
	}
	
	//获取头结点
	public E element() {
	    return getFirst();
	}
	
	//弹出头结点
	public E poll() {
	    final Node<E> f = first;
	    return (f == null) ? null : unlinkFirst(f);
	}
	
	//移除头结点
	public E remove() {
	    return removeFirst();
	}
	
	//在队列尾部添加结点
	public boolean offer(E e) {
	    return add(e);
	}

    //双端队列操作
    
	//在头部添加
	public boolean offerFirst(E e) {
	    addFirst(e);
	    return true;
	}
	
	//在尾部添加
	public boolean offerLast(E e) {
	    addLast(e);
	    return true;
	}
	
	//获取头结点
	public E peekFirst() {
	    final Node<E> f = first;
	    return (f == null) ? null : f.item;
	 }
	
	//获取尾结点
	public E peekLast() {
	    final Node<E> l = last;
	    return (l == null) ? null : l.item;
	}

    //弹出头结点
    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    //弹出尾结点
    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

	//入栈
	public void push(E e) {
	    addFirst(e);
	}
	
	//出栈
	public E pop() {
	    return removeFirst();
	}

    //移除第一次出现的元素
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    //移除最后一次出现的元素
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    //迭代器
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);
        return new ListItr(index);
    }

    private class ListItr implements ListIterator<E> {
        private Node<E> lastReturned = null;
        private Node<E> next;
        private int nextIndex;
        private int expectedModCount = modCount;

        ListItr(int index) {
            // assert isPositionIndex(index);
            next = (index == size) ? null : node(index);
            nextIndex = index;
        }

        public boolean hasNext() {
            return nextIndex < size;
        }

        public E next() {
            checkForComodification();
            if (!hasNext())
                throw new NoSuchElementException();

            lastReturned = next;
            next = next.next;
            nextIndex++;
            return lastReturned.item;
        }

        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        public E previous() {
            checkForComodification();
            if (!hasPrevious())
                throw new NoSuchElementException();

            lastReturned = next = (next == null) ? last : next.prev;
            nextIndex--;
            return lastReturned.item;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void remove() {
            checkForComodification();
            if (lastReturned == null)
                throw new IllegalStateException();

            Node<E> lastNext = lastReturned.next;
            unlink(lastReturned);
            if (next == lastReturned)
                next = lastNext;
            else
                nextIndex--;
            lastReturned = null;
            expectedModCount++;
        }

        public void set(E e) {
            if (lastReturned == null)
                throw new IllegalStateException();
            checkForComodification();
            lastReturned.item = e;
        }

        public void add(E e) {
            checkForComodification();
            lastReturned = null;
            if (next == null)
                linkLast(e);
            else
                linkBefore(e, next);
            nextIndex++;
            expectedModCount++;
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

	//结点内部类
	private static class Node<E> {
	    E item;          //元素
	    Node<E> next;    //下一个节点
	    Node<E> prev;    //上一个节点
	
	    Node(Node<E> prev, E element, Node<E> next) {
	        this.item = element;
	        this.next = next;
	        this.prev = prev;
	    }
	}

    /**
     * @since 1.6
     */
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    /**
     * Adapter to provide descending iterators via ListItr.previous
     */
    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());
        public boolean hasNext() {
            return itr.hasPrevious();
        }
        public E next() {
            return itr.previous();
        }
        public void remove() {
            itr.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<E> superClone() {
        try {
            return (LinkedList<E>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    //克隆集合
    public Object clone() {
        LinkedList<E> clone = superClone();
        clone.first = clone.last = null;
        clone.size = 0;
        clone.modCount = 0;
        for (Node<E> x = first; x != null; x = x.next) {
        	clone.add(x.item);
        }
        return clone;
    }

    //以数组形式返回集合元素
    public Object[] toArray() {
        Object[] result = new Object[size];
        int i = 0;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;
        return result;
    }


    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size) {
        	a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        }
        int i = 0;
        Object[] result = a;
        for (Node<E> x = first; x != null; x = x.next) {
        	result[i++] = x.item;
        }
        if (a.length > size) {
        	a[size] = null;
        }
        return a;
    }

    private static final long serialVersionUID = 876323262645176354L;

    //写入到流
    private void writeObject(jdk7.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (Node<E> x = first; x != null; x = x.next)
            s.writeObject(x.item);
    }

    //从流中读取
    @SuppressWarnings("unchecked")
    private void readObject(jdk7.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            linkLast((E)s.readObject());
    }
}
