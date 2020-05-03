package jdk7.util.collection;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Vector;

public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    
	private static final long serialVersionUID = 8683452581122892189L;

	//默认初始化容量
	private static final int DEFAULT_CAPACITY = 10;
	
	//空对象数组
	private static final Object[] EMPTY_ELEMENTDATA = {};
	
	//对象数组
	private transient Object[] elementData;
	
	//集合元素个数
	private int size;
	
	//传入初始容量的构造方法
	public ArrayList(int initialCapacity) {
	    super();
	    if (initialCapacity < 0) {
	    	throw new IllegalArgumentException("Illegal Capacity: "+ initialCapacity);
	    }
	    //新建指定容量的Object类型数组
	    this.elementData = new Object[initialCapacity];
	}
	
	//不带参数的构造方法
	public ArrayList() {
	    super();
	    //将空的数组实例传给elementData
	    this.elementData = EMPTY_ELEMENTDATA;
	}
	
	//传入外部集合的构造方法
	public ArrayList(Collection<? extends E> c) {
		//持有传入集合的内部数组的引用
	    elementData = c.toArray();
	    //更新集合元素个数大小
	    size = elementData.length;
	    //判断引用的数组类型, 并将引用转换成Object数组引用
	    if (elementData.getClass() != Object[].class) {
	    	elementData = Arrays.copyOf(elementData, size, Object[].class);
	    }
	}

    //减小数组容量
    public void trimToSize() {
        modCount++;
        //如果集合元素个数小于数组长度
        if (size < elementData.length) {
        	//重新构造一个容量为size的数组
            elementData = Arrays.copyOf(elementData, size);
        }
    }

    
    public void ensureCapacity(int minCapacity) {
        int minExpand = (elementData != EMPTY_ELEMENTDATA) ? 0 : DEFAULT_CAPACITY;
        if (minCapacity > minExpand) {
            ensureExplicitCapacity(minCapacity);
        }
    }

    
	private void ensureCapacityInternal(int minCapacity) {
		//如果此时还是空数组
	    if (elementData == EMPTY_ELEMENTDATA) {
	    	//和默认容量比较, 取较大值
	        minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
	    }
	    //数组已经初始化过就执行这一步
	    ensureExplicitCapacity(minCapacity);
	}
	
	private void ensureExplicitCapacity(int minCapacity) {
	    modCount++;
	    //如果最小容量大于数组长度就扩增数组
	    if (minCapacity - elementData.length > 0) {
	    	grow(minCapacity);
	    }
	}
	
	//集合最大容量
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
	
	//增加数组长度
	private void grow(int minCapacity) {
		//获取数组原先的容量
	    int oldCapacity = elementData.length;
	    //新数组的容量, 在原来的基础上增加一半
	    int newCapacity = oldCapacity + (oldCapacity >> 1);
	    //检验新的容量是否小于最小容量
	    if (newCapacity - minCapacity < 0) {
	    	newCapacity = minCapacity;
	    }
	    //检验新的容量是否超过最大数组容量
	    if (newCapacity - MAX_ARRAY_SIZE > 0) {
	    	newCapacity = hugeCapacity(minCapacity);
	    }
	    //拷贝原来的数组到新数组
	    elementData = Arrays.copyOf(elementData, newCapacity);
	}

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) {
        	throw new OutOfMemoryError();
        }
        //MAX_ARRAY_SIZE为Integer.MAX_VALUE, 这已经是最极限了
        return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
    }

    //返回列表的大小
    public int size() {
        return size;
    }

    //判断是否为空
    public boolean isEmpty() {
        return size == 0;
    }

    //判断是否包含了某个元素
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    //找出某个元素在数组的位置
    public int indexOf(Object o) {
        if (o == null) {
            for (int i = 0; i < size; i++) {
            	if (elementData[i]==null) {
            		return i;
            	}
            }
        } else {
            for (int i = 0; i < size; i++) {
            	if (o.equals(elementData[i])) {
            		return i;
            	}
            }
        }
        return -1;
    }

    //返回一个元素最后出现的位置
    public int lastIndexOf(Object o) {
        if (o == null) {
            for (int i = size-1; i >= 0; i--) {
            	if (elementData[i]==null) {
            		return i;
            	}
            } 
        } else {
            for (int i = size-1; i >= 0; i--) {
            	if (o.equals(elementData[i])) {
            		return i;
            	}
            }
        }
        return -1;
    }

    //克隆数组
    public Object clone() {
        try {
            @SuppressWarnings("unchecked")
            ArrayList<E> v = (ArrayList<E>) super.clone();
            v.elementData = Arrays.copyOf(elementData, size);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    //转化成数组对象
    public Object[] toArray() {
        return Arrays.copyOf(elementData, size);
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size) {
        	return (T[]) Arrays.copyOf(elementData, size, a.getClass());
        }
        System.arraycopy(elementData, 0, a, 0, size);
        if (a.length > size) {
        	a[size] = null;
        }
        return a;
    }

    // Positional Access Operations
    @SuppressWarnings("unchecked")
    E elementData(int index) {
        return (E) elementData[index];
    }

	//增(添加)
	public boolean add(E e) {
		//添加前先检查是否需要拓展数组, 此时数组长度最小为size+1
	    ensureCapacityInternal(size + 1);
	    //将元素添加到数组末尾
	    elementData[size++] = e;
	    return true;
	}
	
	
	//增(插入)
	public void add(int index, E element) {
		//插入位置范围检查
	    rangeCheckForAdd(index);
	    //检查是否需要扩容
	    ensureCapacityInternal(size + 1);
	    //挪动插入位置后面的元素
	    System.arraycopy(elementData, index, elementData, index + 1, size - index);
	    //在要插入的位置赋上新值
	    elementData[index] = element;
	    size++;
	}
	
	//删
	public E remove(int index) {
		//index不能大于size
	    rangeCheck(index);
	    modCount++;
	    E oldValue = elementData(index);
	    int numMoved = size - index - 1;
	    if (numMoved > 0) {
	    	//将index后面的元素向前挪动一位
	    	System.arraycopy(elementData, index+1, elementData, index, numMoved);
	    }
	    //置空引用
	    elementData[--size] = null;
	    return oldValue;
	}
	
	//改
	public E set(int index, E element) {
		//index不能大于size
	    rangeCheck(index);
	    E oldValue = elementData(index);
	    //替换成新元素
	    elementData[index] = element;
	    return oldValue;
	}
	
	//查
	public E get(int index) {
		//index不能大于size
	    rangeCheck(index);
	    //返回指定位置元素
	    return elementData(index);
	}

    
    //移除指定的元素
    public boolean remove(Object o) {
        if (o == null) {
            for (int index = 0; index < size; index++)
                if (elementData[index] == null) {
                    fastRemove(index);
                    return true;
                }
        } else {
            for (int index = 0; index < size; index++)
                if (o.equals(elementData[index])) {
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }

    //快速删除指定位置的元素
    private void fastRemove(int index) {
        modCount++;
        int numMoved = size - index - 1;
        if (numMoved > 0) {
        	//向前挪一位
        	System.arraycopy(elementData, index+1, elementData, index, numMoved);
        }
        //置空引用
        elementData[--size] = null;
    }

    //清空集合
    public void clear() {
        modCount++;
        //将数组引用全部置空
        for (int i = 0; i < size; i++) {
        	elementData[i] = null;
        }
        size = 0;
    }

    //往集合后面添加集合
    public boolean addAll(Collection<? extends E> c) {
        Object[] a = c.toArray();
        //新的数组长度
        int numNew = a.length;
        //检查是否要扩容
        ensureCapacityInternal(size + numNew);
        //将新数组添加到原数组后面
        System.arraycopy(a, 0, elementData, size, numNew);
        size += numNew;
        return numNew != 0;
    }

    //在指定位置插入一个集合
    public boolean addAll(int index, Collection<? extends E> c) {
    	//检查位置是否合理
        rangeCheckForAdd(index);
        Object[] a = c.toArray();
        int numNew = a.length;
        //检查是否要扩容
        ensureCapacityInternal(size + numNew);  // Increments modCount
        //获取要移动的元素数量
        int numMoved = size - index;
        if (numMoved > 0) {
        	//向后挪动numNew位
        	System.arraycopy(elementData, index, elementData, index + numNew, numMoved);
        }
        //插入数组
        System.arraycopy(a, 0, elementData, index, numNew);
        size += numNew;
        return numNew != 0;
    }

    //移除指定范围的元素
    protected void removeRange(int fromIndex, int toIndex) {
        modCount++;
        int numMoved = size - toIndex;
        System.arraycopy(elementData, toIndex, elementData, fromIndex, numMoved);

        // clear to let GC do its work
        int newSize = size - (toIndex-fromIndex);
        for (int i = newSize; i < size; i++) {
            elementData[i] = null;
        }
        size = newSize;
    }

    //范围检查
    private void rangeCheck(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    //范围检查
    private void rangeCheckForAdd(int index) {
        if (index > size || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    //数组越界的消息
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    //移除给定集合包含的所有元素
    public boolean removeAll(Collection<?> c) {
        return batchRemove(c, false);
    }

    //仅保留给定集合的元素
    public boolean retainAll(Collection<?> c) {
        return batchRemove(c, true);
    }

    //批次移除
    private boolean batchRemove(Collection<?> c, boolean complement) {
        final Object[] elementData = this.elementData;
        int r = 0, w = 0;
        boolean modified = false;
        try {
        	//遍历比较数组元素
            for (; r < size; r++) {
            	if (c.contains(elementData[r]) == complement) {
            		elementData[w++] = elementData[r];
            	}
            }
        } finally {
            // Preserve behavioral compatibility with AbstractCollection,
            // even if c.contains() throws.
            if (r != size) {
                System.arraycopy(elementData, r, elementData, w, size - r);
                w += size - r;
            }
            if (w != size) {
                // clear to let GC do its work
                for (int i = w; i < size; i++) {
                	elementData[i] = null;
                }
                modCount += size - w;
                size = w;
                modified = true;
            }
        }
        return modified;
    }

    //写入到流
    private void writeObject(jdk7.io.ObjectOutputStream s)
        throws java.io.IOException{
        // Write out element count, and any hidden stuff
        int expectedModCount = modCount;
        s.defaultWriteObject();

        // Write out size as capacity for behavioural compatibility with clone()
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (int i=0; i<size; i++) {
            s.writeObject(elementData[i]);
        }

        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    //从流中读取
    private void readObject(jdk7.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        elementData = EMPTY_ELEMENTDATA;

        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in capacity
        s.readInt(); // ignored

        if (size > 0) {
            // be like clone(), allocate array based upon size not capacity
            ensureCapacityInternal(size);

            Object[] a = elementData;
            // Read in all elements in the proper order.
            for (int i=0; i<size; i++) {
                a[i] = s.readObject();
            }
        }
    }

    //集合迭代器
    public ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size) {
        	throw new IndexOutOfBoundsException("Index: "+index);
        }
        return new ListItr(index);
    }

    public ListIterator<E> listIterator() {
        return new ListItr(0);
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    //自己内部实现了迭代器
    private class Itr implements Iterator<E> {
        int cursor;       // index of next element to return
        int lastRet = -1; // index of last element returned; -1 if no such
        int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor != size;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            checkForComodification();
            int i = cursor;
            if (i >= size) {
            	throw new NoSuchElementException();
            }
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length) {
            	throw new ConcurrentModificationException();
            }
            cursor = i + 1;
            return (E) elementData[lastRet = i];
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.remove(lastRet);
                cursor = lastRet;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    //一个优化版本的迭代器
    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            super();
            cursor = index;
        }

        public boolean hasPrevious() {
            return cursor != 0;
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        @SuppressWarnings("unchecked")
        public E previous() {
            checkForComodification();
            int i = cursor - 1;
            if (i < 0)
                throw new NoSuchElementException();
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                throw new ConcurrentModificationException();
            cursor = i;
            return (E) elementData[lastRet = i];
        }

        public void set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.set(lastRet, e);
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void add(E e) {
            checkForComodification();

            try {
                int i = cursor;
                ArrayList.this.add(i, e);
                cursor = i + 1;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

    //返回指定范围的集合元素
    public List<E> subList(int fromIndex, int toIndex) {
        subListRangeCheck(fromIndex, toIndex, size);
        return new SubList(this, 0, fromIndex, toIndex);
    }

    //范围检查
    static void subListRangeCheck(int fromIndex, int toIndex, int size) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > size)
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                               ") > toIndex(" + toIndex + ")");
    }

    //子集合
    private class SubList extends AbstractList<E> implements RandomAccess {
        private final AbstractList<E> parent;
        private final int parentOffset;
        private final int offset;
        int size;

        SubList(AbstractList<E> parent, int offset, int fromIndex, int toIndex) {
            this.parent = parent;
            this.parentOffset = fromIndex;
            this.offset = offset + fromIndex;
            this.size = toIndex - fromIndex;
            this.modCount = ArrayList.this.modCount;
        }

        public E set(int index, E e) {
            rangeCheck(index);
            checkForComodification();
            E oldValue = ArrayList.this.elementData(offset + index);
            ArrayList.this.elementData[offset + index] = e;
            return oldValue;
        }

        public E get(int index) {
            rangeCheck(index);
            checkForComodification();
            return ArrayList.this.elementData(offset + index);
        }

        public int size() {
            checkForComodification();
            return this.size;
        }

        public void add(int index, E e) {
            rangeCheckForAdd(index);
            checkForComodification();
            parent.add(parentOffset + index, e);
            this.modCount = parent.modCount;
            this.size++;
        }

        public E remove(int index) {
            rangeCheck(index);
            checkForComodification();
            E result = parent.remove(parentOffset + index);
            this.modCount = parent.modCount;
            this.size--;
            return result;
        }

        protected void removeRange(int fromIndex, int toIndex) {
            checkForComodification();
            parent.removeRange(parentOffset + fromIndex,
                               parentOffset + toIndex);
            this.modCount = parent.modCount;
            this.size -= toIndex - fromIndex;
        }

        public boolean addAll(Collection<? extends E> c) {
            return addAll(this.size, c);
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            rangeCheckForAdd(index);
            int cSize = c.size();
            if (cSize==0)
                return false;

            checkForComodification();
            parent.addAll(parentOffset + index, c);
            this.modCount = parent.modCount;
            this.size += cSize;
            return true;
        }

        public Iterator<E> iterator() {
            return listIterator();
        }

        public ListIterator<E> listIterator(final int index) {
            checkForComodification();
            rangeCheckForAdd(index);
            final int offset = this.offset;

            return new ListIterator<E>() {
                int cursor = index;
                int lastRet = -1;
                int expectedModCount = ArrayList.this.modCount;

                public boolean hasNext() {
                    return cursor != SubList.this.size;
                }

                @SuppressWarnings("unchecked")
                public E next() {
                    checkForComodification();
                    int i = cursor;
                    if (i >= SubList.this.size)
                        throw new NoSuchElementException();
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i + 1;
                    return (E) elementData[offset + (lastRet = i)];
                }

                public boolean hasPrevious() {
                    return cursor != 0;
                }

                @SuppressWarnings("unchecked")
                public E previous() {
                    checkForComodification();
                    int i = cursor - 1;
                    if (i < 0)
                        throw new NoSuchElementException();
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i;
                    return (E) elementData[offset + (lastRet = i)];
                }

                public int nextIndex() {
                    return cursor;
                }

                public int previousIndex() {
                    return cursor - 1;
                }

                public void remove() {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        SubList.this.remove(lastRet);
                        cursor = lastRet;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void set(E e) {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        ArrayList.this.set(offset + lastRet, e);
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void add(E e) {
                    checkForComodification();

                    try {
                        int i = cursor;
                        SubList.this.add(i, e);
                        cursor = i + 1;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                final void checkForComodification() {
                    if (expectedModCount != ArrayList.this.modCount)
                        throw new ConcurrentModificationException();
                }
            };
        }

        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return new SubList(this, offset, fromIndex, toIndex);
        }

        private void rangeCheck(int index) {
            if (index < 0 || index >= this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private void rangeCheckForAdd(int index) {
            if (index < 0 || index > this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private String outOfBoundsMsg(int index) {
            return "Index: "+index+", Size: "+this.size;
        }

        private void checkForComodification() {
            if (ArrayList.this.modCount != this.modCount)
                throw new ConcurrentModificationException();
        }
    }
}
