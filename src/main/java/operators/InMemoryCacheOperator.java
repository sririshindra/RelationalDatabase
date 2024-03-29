package operators;

import net.sf.jsqlparser.expression.PrimitiveValue;
import utils.Utils;

import java.util.*;

public class InMemoryCacheOperator implements Operator {
    Operator child;
    Map<String, PrimitiveValue> childTuple;
    Map<String, Integer> colNameToIdx;
    Map<Integer, String> idxToColName;
    List<List<PrimitiveValue>> cacheMemory;
    boolean isCached;
    Map<String, PrimitiveValue> returnTuple;
    Iterator<List<PrimitiveValue>> cacheIterator;
    private Map<String, Integer> schema;
    boolean isFirstCall;

    public InMemoryCacheOperator(Operator child) {
        colNameToIdx = new LinkedHashMap<String, Integer>();
        idxToColName = new LinkedHashMap<Integer, String>();
        returnTuple = new LinkedHashMap<String, PrimitiveValue>();
        cacheMemory = new ArrayList<List<PrimitiveValue>>();
        this.child = child;
        setSchema();
        isCached = false;
        isFirstCall = true;
    }

    private void setSchema() {
        schema = child.getSchema();
    }

    public Map<String, Integer> getSchema() {
        return schema;
    }

    public Operator getChild() {
        return child;
    }

    public void setChild(Operator child) {
        this.child = child;
    }

    public Map<String, PrimitiveValue> next() {
        if (isFirstCall) {
            this.childTuple = child.next();
            Utils.fillColIdx(childTuple, colNameToIdx, idxToColName);
            isFirstCall = false;
        }
        if (childTuple == null) {
            isCached = true;
            return null;
        }
        if (isCached) {
            returnTuple = childTuple;
            if (cacheIterator.hasNext()) {
                childTuple = Utils.convertToMap(cacheIterator.next(), idxToColName);
            } else {
                childTuple = null;
            }
            return returnTuple;
        } else {
            cacheMemory.add(Utils.convertToList(childTuple, colNameToIdx));
            returnTuple.putAll(childTuple);
            childTuple = child.next();
            return returnTuple;
        }

    }

    public void init() {
        cacheIterator = cacheMemory.iterator();
        childTuple = Utils.convertToMap(cacheIterator.next(), idxToColName);
    }
}
