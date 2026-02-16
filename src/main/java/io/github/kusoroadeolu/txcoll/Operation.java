package io.github.kusoroadeolu.txcoll;

public sealed interface Operation permits Operation.PutOperation, Operation.RemoveOperation, Operation.UpdateOperation, Operation.ContainsKeyOperation, Operation.SizeOperation, Operation.GetOperation {
     record PutOperation<V>(V v) implements Operation {
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

        record UpdateOperation<V>(V v) implements Operation{
            @Override
            public boolean equals(Object object) {
                return object != null && getClass() == object.getClass();
            }

            @Override
            public int hashCode() {
                return UpdateOperation.class.hashCode();
            }
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
