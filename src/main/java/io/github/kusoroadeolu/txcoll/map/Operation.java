package io.github.kusoroadeolu.txcoll.map;

public sealed interface Operation permits Operation.PutOperation, Operation.RemoveOperation, Operation.ContainsKeyOperation, Operation.SizeOperation, Operation.GetOperation {
     record PutOperation<V>(V value) implements Operation {
            @Override
            public boolean equals(Object object) {
                return object != null && getClass() == object.getClass();
            }

            @Override
            public int hashCode() {
                return PutOperation.class.hashCode();
            }
        }

        enum RemoveOperation implements Operation{
            REMOVE
        }

        enum GetOperation implements Operation{
             GET
        }

        enum ContainsKeyOperation implements Operation{
             CONTAINS
        }

        enum SizeOperation implements Operation{
            SIZE
        }

}
