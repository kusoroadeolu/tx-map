import io.github.kusoroadeolu.txcoll.FutureValue;

ReadWriteLock lock = new ReentrantReadWriteLock();

void main() {
    FutureValue<Integer> task = new FutureValue<>();
    task.get();

}