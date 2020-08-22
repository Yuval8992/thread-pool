package il.co.ilrd.thread_pool;


import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;


public class WaitableQueue<E> {
	private Queue<E> priorityQueue = null;
	private Object monitor = new Object();
	
	public WaitableQueue() {
		priorityQueue = new PriorityQueue<>();
	}
	
	public WaitableQueue(Comparator<E> comparator) {
		priorityQueue = new PriorityQueue<>(11, comparator);
	}
	
	public void enqueue(E task) {
		synchronized (monitor) {
			priorityQueue.offer(task);		
			monitor.notify();
		}
	}
	
	public E dequeue() {
		synchronized (monitor) {
			while(priorityQueue.isEmpty()) {
				try {
					monitor.wait();
				} catch (InterruptedException e) {}
			}	
			return priorityQueue.poll();
		}	
	}
	
	public E dequeueTimeout(long time, TimeUnit unit) {
		synchronized (monitor) {
			while(priorityQueue.isEmpty()) {
				try {
					unit.timedWait(monitor, time);
				} catch (InterruptedException e) {}
			}	
			return priorityQueue.poll();
		}
	}
	
	public boolean remove(E task) {
		return priorityQueue.remove(task);		
	}
	
}

