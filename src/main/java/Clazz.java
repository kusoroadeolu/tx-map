import io.github.kusoroadeolu.txcoll.FutureValue;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap;

void main() {
    TransactionalMap<String, Integer> txMap = new TransactionalMap<>();
    List<FutureValue<Integer>> ls = new ArrayList<>();
    try(var tx = txMap.beginTx()) {
        tx.put("1", 2);
        FutureValue<Integer> f1 = tx.get("1");
        ls.add(f1);
    }

    ls.forEach(IO::println);
}