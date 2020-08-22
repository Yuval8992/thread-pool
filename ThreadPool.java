package il.co.ilrd.thread_pool;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ThreadPool {	
	private WaitableQueue<Task<?>> waitableQueue = new WaitableQueue<>();
	private WaitableQueue<Task<?>> refToQueue = waitableQueue;
	private Semaphore pauseSem = new Semaphore(0);
	private Semaphore shutDownSem = new Semaphore(0);
	private int numOfThreads = 0;
	private boolean isPaused = false;	
	
	 enum PRIORITY {
		LOW,
		MEDIUM,
		HIGH;
	}
	
	public ThreadPool(int numOfThreads) {
		this.numOfThreads = numOfThreads;

		for (int i = 0; i < numOfThreads; ++i) {
			new Thread(new RunningThread()).start();
		}
	}
	
	private class RunningThread implements Runnable {
		private boolean isRunning = true; 
		
		@Override
		public void run() {
			while (isRunning) {
				isRunning = refToQueue.dequeue().futureTask.run();
			}
		}
	}
	
	private class Task<T> implements Comparable<Task<T>> {
		private FutureTask<T> futureTask = null;
		private int priority = 0;
		
		private Task(Callable<T> callable, PRIORITY priority) {
			futureTask = new FutureTask<>(callable);
			this.priority = priority.ordinal();
		}
		
		private Task(Runnable runnable, PRIORITY priority, T value) {
			futureTask = new FutureTask<>(Executors.callable(runnable, value));
			this.priority = priority.ordinal();
		}
		
		private Task(Runnable runnable, int priority, boolean shouldContinue) {
			futureTask = new FutureTask<>(Executors.callable(runnable, null), shouldContinue);
			this.priority = priority;
		}
		

		private class FutureTask<V> implements Future<V> {
			private Callable<V> callable = null;
			private V result = null;
			private boolean shouldContinue = true;
			private boolean isDone = false;
			private boolean isCanceled = false;
			private Object monitor = new Object();
			
			private FutureTask(Callable<V> callable) {
				this.callable = callable;
			}
			
			private FutureTask(Callable<V> callable, boolean shouldContinue) {
				this.callable = callable;
				this.shouldContinue = shouldContinue;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				isCanceled = waitableQueue.remove(Task.this);
				isDone = true;
				synchronized (monitor) {
					monitor.notifyAll();					
				}
				
				return isCanceled;
			}

			@Override
			public V get() throws InterruptedException, ExecutionException {
				synchronized (monitor) {
					if (!isDone) {
						monitor.wait();											
					}
				}
							
				return result;
			}

			@Override
			public V get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				synchronized (monitor) {
					if (!isDone) {
						unit.timedWait(monitor, timeout);													
					}
					
					return result;					
				}					
			}

			@Override
			public boolean isCancelled() {				
				return isCanceled;
			}

			@Override
			public boolean isDone() {				
					return isDone == true;					
				}

			public boolean run() {
				try {
					result = callable.call();
				} catch (Exception e) {}
				finally {
					synchronized (monitor) {
						isDone = true;	
						monitor.notifyAll();
					}					
				}
				
				return shouldContinue;
			}			
		}
		
		@Override
		public int compareTo(Task<T> task) {
			return task.priority - priority;
		}
	}
	 
	 	 
	public Future<Void> submit(Runnable runnable, PRIORITY priority) {
		Task<Void> task = new Task<>(runnable, priority, null);
		try {
			waitableQueue.enqueue(task);
		}catch (NullPointerException e){
			throw new RejectedExecutionException();
		}
		
		return task.futureTask;
	}
	
	public <T>Future<T> submit(Runnable runnable, PRIORITY priority, T value) {
		Task<T> task = new Task<>(runnable, priority, value);
		try {
			waitableQueue.enqueue(task);
		}catch (NullPointerException e){
			throw new RejectedExecutionException();
		}
		
		return task.futureTask;
	}
	
	public <T> Future<T> submit(Callable<T> callable) {
		Task<T> task = new Task<>(callable, PRIORITY.MEDIUM);
		try {
			waitableQueue.enqueue(task);
		}catch (NullPointerException e){
			throw new RejectedExecutionException();
		}
		
		return task.futureTask;
	}
	
	public <T>Future<T> submit(Callable<T> callable, PRIORITY priority) {
		Task<T> task = new Task<>(callable, priority);
		try {
			waitableQueue.enqueue(task);
		}catch (NullPointerException e){
			throw new RejectedExecutionException();
		}
		
		return task.futureTask;
	}
	
	public void setNumOfThreads(int newNumOfThreads) {
		 if(newNumOfThreads > numOfThreads) {
			for (int i = 0; i < (newNumOfThreads - numOfThreads); ++i) {
				new Thread(new RunningThread()).start();
			}
		 }
		else {
			for (int i = 0; i < (numOfThreads - newNumOfThreads); ++i) {
				waitableQueue.enqueue(new Task<>(()->{}, 3, false));					
			}
		}
		 numOfThreads = newNumOfThreads;
	}
	
	public void pause() {
		isPaused = true;
		Runnable pauseTask = ()->{
			try {
				pauseSem.acquire();
			} catch (InterruptedException e) {}};
					
		for (int i = 0; i < numOfThreads; ++i) {
			waitableQueue.enqueue(new Task<>(pauseTask, 3, true));
		}
	}
	
	public void resume() {
		isPaused = false;
		pauseSem.release(numOfThreads);			
	}
	
	public void shutDown() {
		if(isPaused) {
			throw new IllegalStateException();
		}
		waitableQueue = null;
		
		for (int i = 0; i < numOfThreads; ++i) {	
			refToQueue.enqueue(new Task<>(()->{shutDownSem.release();}, -1, false));		
		}
	}

	public void awaitTermination() {
		for (int i = 0; i < numOfThreads; i++) {
			try {
				shutDownSem.acquire();
			} catch (InterruptedException e) {}
		}
	}
}	 
		

	
	
	
