ReadWriteLock lock = new ReentrantReadWriteLock();

void main() throws ExecutionException, InterruptedException, TimeoutException {
    lock.readLock().lock();
        IO.println("Acquired r lock");
    lock.readLock().unlock();
    lock.writeLock().lock();
        IO.println("Acquired w lock");
    lock.writeLock().unlock();
}