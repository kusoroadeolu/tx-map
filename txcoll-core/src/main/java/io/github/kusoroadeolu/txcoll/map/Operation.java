package io.github.kusoroadeolu.txcoll.map;

public sealed interface Operation permits Operation.ModifyOperation, Operation.ContainsKeyOperation, Operation.SizeOperation, Operation.GetOperation {
     record ModifyOperation<E>(E element, ModifyType type) implements Operation{
         @Override
         public boolean equals(Object object) {
             return object != null && getClass() == object.getClass();
         }

         @Override
         public int hashCode() {
             return ModifyOperation.class.hashCode();
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

        enum ModifyType{
            PUT, REMOVE
        }

}
