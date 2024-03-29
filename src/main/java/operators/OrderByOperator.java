package operators;

import net.sf.jsqlparser.eval.Eval;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.PrimitiveValue;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.PrimitiveType;
import net.sf.jsqlparser.statement.select.OrderByElement;
import utils.TupleSerializer;
import utils.Utils;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

public class OrderByOperator implements Operator,SingleChildOperator {
    static int mergefilecount = 0;
    Operator child;
    List<OrderByElement> orderByElements;
    int counter = -1;
    Map<String, PrimitiveValue> childTuple;
    Map<String, Integer> colNameToIdx;
    Map<Integer, String> idxToColName;
    List<List<PrimitiveValue>> serializedChildTuples;
    Map<String, PrimitiveValue> firstTuple;
    boolean isFirstCall;
    long maxInMemoryTuples;
    List<String> sortedFiles;
    int fileNameCounter;
    long mergeNumber;
    String directoryName;
    String finalSortedFileName;
    PriorityQueue<PriorityQueueTuple> diskSortPriorityQueue;

    public CompareTuples getCompareTuples() {
        return compareTuples;
    }

    public void setCompareTuples(CompareTuples compareTuples) {
        this.compareTuples = compareTuples;
    }

    private CompareTuples compareTuples;
    private Map<String, Integer> schema;
    private BufferedReader sortedFileBufferedReader;
    private TupleSerializer tupleSerializer;

    public OrderByOperator(List<OrderByElement> orderByElements, Operator operator) {
        this.orderByElements = orderByElements;
        this.child = operator;
        setSchema();
        colNameToIdx = new LinkedHashMap<String, Integer>();
        idxToColName = new LinkedHashMap<Integer, String>();
        isFirstCall = true;
        compareTuples = new CompareTuples();
    }

    public OrderByOperator(List<OrderByElement> orderByElements, Operator operator, Map<String, PrimitiveValue> firstTuple) {
        this.orderByElements = orderByElements;
        this.child = operator;
        setSchema();
        this.childTuple = firstTuple;
        colNameToIdx = new LinkedHashMap<String, Integer>();
        idxToColName = new LinkedHashMap<Integer, String>();
        Utils.fillColIdx(childTuple, colNameToIdx, idxToColName);
        isFirstCall = true;
        compareTuples = new CompareTuples();
    }

    private void setSchema() {
        this.schema = child.getSchema();
    }

    public Map<String, Integer> getSchema() {
        return schema;
    }

    public Map<String, PrimitiveValue> next() {
        if (Utils.inMemoryMode)
            return inMemorySortNext();
        else
            return onDiskSortNext();
    }

    private Map<String, PrimitiveValue> inMemorySortNext() {
        if (isFirstCall) {
            if (childTuple == null){
                childTuple = child.next();
            }
            Utils.fillColIdx(childTuple, colNameToIdx, idxToColName);
            isFirstCall = false;
            maxInMemoryTuples = 1000000;
            serializedChildTuples = new ArrayList<List<PrimitiveValue>>();
            populateChildTuples();
            Collections.sort(serializedChildTuples, compareTuples);
        }
        if (++counter < serializedChildTuples.size()) {
            return Utils.convertToMap(serializedChildTuples.get(counter), idxToColName);
        } else {
            serializedChildTuples.clear();
            serializedChildTuples = null;
            return null;
        }
    }

    private Map<String, PrimitiveValue> readNextTupleFromSortedFile() {
        List<PrimitiveValue> primValTuple = null;
        try {
            String currentLine = sortedFileBufferedReader.readLine();
            if (currentLine == null) {
                closeInputStream(sortedFileBufferedReader);
                return null;
            }
            primValTuple = tupleSerializer.deserialize(currentLine);
        } catch (EOFException e) {
            closeInputStream(sortedFileBufferedReader);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Utils.convertToMap(primValTuple, idxToColName);
    }

    private Map<String, PrimitiveValue> onDiskSortNext() {
        if (!isFirstCall) {
            return Utils.convertToMap(getNextValueAndUpdateQueue(), idxToColName);
        }
        if (childTuple == null){
            childTuple = child.next();
        }
        Utils.fillColIdx(childTuple, colNameToIdx, idxToColName);
        isFirstCall = false;
        fileNameCounter = 0;
        directoryName = "sorted_files_" + UUID.randomUUID();
        finalSortedFileName = directoryName + "/final_sorted_file" + UUID.randomUUID();
        serializedChildTuples = new ArrayList<List<PrimitiveValue>>();
        tupleSerializer = new TupleSerializer();
        new File(directoryName).mkdir();
        maxInMemoryTuples = 30000;
        while (childTuple != null) {
            populateChildTuples();
            String fileName = getNewFileName();
            sortAndWriteToFile(fileName);
        }
        child = null;
        serializedChildTuples = null;
        initPriorityQueue();
        return Utils.convertToMap(getNextValueAndUpdateQueue(), idxToColName);
    }



    private String getNewFileName() {
        fileNameCounter++;
        return getFileName(fileNameCounter);
    }

    private String getFileName(int fileNameCounter) {
        return String.valueOf(directoryName) + "/" + String.valueOf(fileNameCounter);
    }
    private void sortAndWriteToFile(String fileName) {
        Collections.sort(serializedChildTuples, compareTuples);
        try {
            BufferedWriter bufferedWriter = openBufferedWriter(fileName);
            for (List<PrimitiveValue> serializedChildTuple : serializedChildTuples) {
                bufferedWriter.write(tupleSerializer.serialize(serializedChildTuple) + "\n");
            }
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        serializedChildTuples.clear();
    }

    private BufferedWriter openBufferedWriter(String fileName) throws IOException {
        BufferedWriter fos;
        File file = new File(fileName);
        FileWriter fw = new FileWriter(file);
        fos = new BufferedWriter(fw);
        return fos;
    }

    public void initPriorityQueue() {
        int i = 1;
        diskSortPriorityQueue = new PriorityQueue<PriorityQueueTuple>();
        while (i <= fileNameCounter) {
            String fileName = getFileName(i);
            BufferedReader bis = openBufferedInput(fileName);
            insertInPriorityQueue(bis);
            i++;
        }

    }

    private BufferedReader openBufferedInput(String fileName) {
        BufferedReader bufferedReader = null;
        try {
            FileReader fileReader = new FileReader(fileName);
            bufferedReader = new BufferedReader(fileReader);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bufferedReader;
    }

    private void insertInPriorityQueue(BufferedReader bis) {
        List<PrimitiveValue> tuple = null;
        try {
            String oisString = bis.readLine();
            if (oisString == null) {
                closeInputStream(bis);
                return;
            }
            tuple = tupleSerializer.deserialize(oisString);

        } catch (EOFException e) {
            closeInputStream(bis);
            return;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        PriorityQueueTuple priorityQueueTuple = new PriorityQueueTuple(tuple, bis);
        diskSortPriorityQueue.add(priorityQueueTuple);
    }

    private void closeInputStream(BufferedReader bufferedReader) {
        try {
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    private List<PrimitiveValue> getNextValueAndUpdateQueue() {
        PriorityQueueTuple priorityQueueTuple = diskSortPriorityQueue.poll();
        if (priorityQueueTuple == null)
            return null;
        List<PrimitiveValue> tuple = priorityQueueTuple.getTuple();
        BufferedReader tupleBis = priorityQueueTuple.getTupleBufferedReader();
        insertInPriorityQueue(tupleBis);
        return tuple;
    }


    public void init() {

    }


    private void populateChildTuples() {
        long counter = 0;
        if (firstTuple != null) {
            serializedChildTuples.add(Utils.convertToList(firstTuple, colNameToIdx));
            firstTuple = null;
        }
        while (childTuple != null && counter < maxInMemoryTuples) {
            List<PrimitiveValue> serializedChildTuple = Utils.convertToList(childTuple, colNameToIdx);
            serializedChildTuples.add(serializedChildTuple);
            childTuple = child.next();
            counter++;
        }

    }

    @Override
    public Operator getChild() {
        return child;
    }

    @Override
    public void setChild(Operator child) {
        this.child = child;
    }


    public class CompareTuples extends Eval implements Comparator<List<PrimitiveValue>> {

        Map<String, PrimitiveValue> currTuple;

        public int compare(List<PrimitiveValue> o1, List<PrimitiveValue> o2) {
            return compareMaps(Utils.convertToMap(o1, idxToColName), Utils.convertToMap(o2, idxToColName));
        }

        public int compareMaps(Map<String, PrimitiveValue> o1, Map<String, PrimitiveValue> o2) {

//            if (o1 == null || o2 == null){
//                System.out.println(o1);
//                System.out.println(o2);
//            }

//            if(o1 == null){
//                return -1;
//            }
//            if (o2 == null){
//                return 1;
//            }


            for (OrderByElement element : orderByElements) {
                Expression expression = element.getExpression();
                boolean isAsc = element.isAsc();

                PrimitiveValue value1 = evaluate(expression, o1);
                PrimitiveValue value2 = evaluate(expression, o2);
                if (value1.getType().equals(PrimitiveType.DOUBLE)) {
                    try {
                        double val1 = value1.toDouble();
                        double val2 = value2.toDouble();
                        if (val1 < val2) {
                            return isAsc ? -1 : 1;
                        } else if (val1 > val2) {
                            return isAsc ? 1 : -1;
                        }
                    } catch (PrimitiveValue.InvalidPrimitive throwables) {
                        throwables.printStackTrace();
                    }
                } else if (value1.getType().equals(PrimitiveType.LONG)) {
                    try {
                        long val1 = value1.toLong();
                        long val2 = value2.toLong();
                        if (val1 < val2) {
                            return isAsc ? -1 : 1;
                        } else if (val1 > val2) {
                            return isAsc ? 1 : -1;
                        }
                    } catch (PrimitiveValue.InvalidPrimitive throwables) {
                        throwables.printStackTrace();
                    }
                } else if (value1.getType().equals(PrimitiveType.STRING)) {
                    String val1 = value1.toRawString();
                    String val2 = value2.toRawString();
                    int compare = val1.compareTo(val2);
                    if (compare == 0) continue;
                    if (isAsc) return compare;
                    else return (-1 * compare);

                }

            }
            return 0;
        }

        public PrimitiveValue evaluate(Expression expression, Map<String, PrimitiveValue> tuple) {
            currTuple = tuple;
            PrimitiveValue value = null;
            try {
                value = eval(expression);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return value;
        }

        public PrimitiveValue eval(Column x) {

            String colName = x.getColumnName();
            String tableName = x.getTable().getName();
            return Utils.getColValue(tableName, colName, currTuple);
        }
    }

    class PriorityQueueTuple implements Comparable<PriorityQueueTuple> {
        private List<PrimitiveValue> tuple;
        private BufferedReader tupleOis;

        public PriorityQueueTuple(List<PrimitiveValue> tuple, BufferedReader tupleOis) {
            this.tuple = tuple;
            this.tupleOis = tupleOis;
        }


        public int compareTo(PriorityQueueTuple o) {
            return compareTuples.compare(this.tuple, o.tuple);
        }

        public List<PrimitiveValue> getTuple() {
            return tuple;
        }

        public BufferedReader getTupleBufferedReader() {
            return tupleOis;
        }
    }


}


