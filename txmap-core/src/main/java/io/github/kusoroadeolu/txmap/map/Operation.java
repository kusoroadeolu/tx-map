package io.github.kusoroadeolu.txmap.map;

import static io.github.kusoroadeolu.txmap.map.Operation.ModifyType.REMOVE;

public sealed interface Operation permits Operation.ModifyOperation, Operation.ContainsKeyOperation, Operation.SizeOperation, Operation.GetOperation {
    ModifyOperation<?> DEFAULT_MODIFY_OP = new ModifyOperation<>(null, REMOVE);

    record ModifyOperation<E>(E element, ModifyType type) implements Operation{
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
