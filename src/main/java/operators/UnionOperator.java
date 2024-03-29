package operators;

import net.sf.jsqlparser.expression.PrimitiveValue;

import java.util.Map;

public class UnionOperator implements Operator, DoubleChildOperator {
    private Operator leftOperator;
    private Operator rightOperator;
    private boolean isleftExhausted;

    public UnionOperator(Operator leftOperator, Operator rightOperator) {
        this.leftOperator = leftOperator;
        this.rightOperator = rightOperator;
        isleftExhausted = false;
    }

    public Map<String, PrimitiveValue> next() {
        Map<String, PrimitiveValue> tuple = null;
        if (!isleftExhausted){
            tuple = leftOperator.next();
            if (tuple == null){
                isleftExhausted = true;
            }

        }
        if (isleftExhausted){
            tuple = rightOperator.next();
        }
        return tuple;
//        Map<String, PrimitiveValue> tuple = leftOperator.next();
//        if (tuple == null) {
//            tuple = rightOperator.next();
//        }
//        return tuple;
    }

    public void init() {

    }

    public Map<String, Integer> getSchema() {
        return leftOperator.getSchema();
    }

    @Override
    public Operator getLeftChild() {
        return leftOperator;
    }

    @Override
    public Operator getRightChild() {
        return rightOperator;
    }

    @Override
    public void setLeftChild(Operator leftChild) {
        this.leftOperator = leftChild;
    }

    @Override
    public void setRightChild(Operator rightChild) {
        this.rightOperator = rightChild;
    }
}
