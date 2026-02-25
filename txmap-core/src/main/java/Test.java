import io.github.kusoroadeolu.txmap.TransactionalMap;

void main(){
    TransactionalMap<String, Integer> map = TransactionalMap.createPessimistic();
    try (var tx = map.beginTx()){
        tx.size();
        tx.put("2", 2);
        tx.commit();
    }
}