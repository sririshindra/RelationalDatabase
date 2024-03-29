package preCompute;

import Indexes.IndexFactory;
import Indexes.PrimaryIndex;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.PrimitiveValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubSelect;
import utils.Constants;
import utils.Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.*;

public class PreComputeLoader {

    private IndexFactory indexFactory = new IndexFactory();

    public void loadSavedState() {
        loadSchemas();
        loadTableLines();
        loadColToByteCnt();
        loadViewExps();
        //loadIndexes();
        loadStatistics();
    }

    private void loadStatistics() {
        File minFile = new File(Constants.MIN_MAX_COL_DIR,Constants.MIN_FILE_NAME);
        File maxFile = new File(Constants.MIN_MAX_COL_DIR,Constants.MAX_FILE_NAME);
        List<String> minLines = getAllLinesFromFile(minFile);
        List<String> maxLines = getAllLinesFromFile(maxFile);
        splitAndInsert(minLines,Utils.colToMin);
        splitAndInsert(maxLines,Utils.colToMax);
    }

    private void splitAndInsert(List<String> lines, Map<String, PrimitiveValue> map) {
        for (String line: lines) {
            String parts[] = line.split(",");
            String colName = parts[0];
            String val = parts[1];
            String dataType = Utils.colToColDef.get(colName).getColDataType().getDataType();
            PrimitiveValue primitiveValue = Utils.getPrimitiveValue(dataType,val);
            map.put(colName,primitiveValue);
        }
    }

    private List<String> getAllLinesFromFile(File file) {
        List<String> allLines = new ArrayList<String>();
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            String line = null;
            while((line = bufferedReader.readLine())!= null) {
                allLines.add(line);
            }
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return allLines;
    }


    private void loadViewExps() {
        File viewExpsDir = new File(Constants.VIEW_EXPS_DIR);
        File[] expFiles = viewExpsDir.listFiles();
        for (File expFile : expFiles) {
            try {
                FileInputStream fileInputStream = new FileInputStream(expFile);
                CCJSqlParser ccjSqlParser = new CCJSqlParser(fileInputStream);
                Select selectStatement = (Select) ccjSqlParser.Statement();
                PlainSelect plainSelect = (PlainSelect)selectStatement.getSelectBody();
                Utils.viewToExpression.put(expFile.getName(),plainSelect.getWhere());

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }

        }
    }

    private void loadColToByteCnt() {
        File dir = new File(Constants.COLUMN_BYTES_DIR);
        File[] files = dir.listFiles();
        for (File file : files) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                DataInputStream dataInputStream = new DataInputStream(fileInputStream);
                Long byteCnt = dataInputStream.readLong();
                Utils.colToByteCnt.put(file.getName(), byteCnt);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void loadTableLines() {
        File dir = new File(Constants.LINES_DIR);
        File[] files = dir.listFiles();
        for (File file : files) {
            try {
                DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
                Utils.tableToLines.put(file.getName(), dataInputStream.readInt());
                dataInputStream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadIndex(File indexFile) {
        PrimaryIndex primaryIndex = indexFactory.getIndex(indexFile);
        Utils.colToPrimIndex.put(indexFile.getName(), primaryIndex);
    }

    private void loadIndexes() {
        File primaryIndexesDirFile = new File(Constants.PRIMARY_INDICES_DIR);
        File[] files = primaryIndexesDirFile.listFiles();
        for (File indexFile : files) {
            loadIndex(indexFile);
        }
    }

    private void loadSchemas() {
        File dir = new File(Constants.COL_DEFS_DIR);
        File[] colDefFiles = dir.listFiles();
        for (File colDefsFile : colDefFiles) {
            saveTableSchema(colDefsFile);
        }
        File viewSchemasDir = new File(Constants.VIEW_SCHEMA_DIR);
        File[] viewSchemaFiles = viewSchemasDir.listFiles();
        for (File viewSchemaFile : viewSchemaFiles) {
            loadViewSchema(viewSchemaFile);
        }
    }

    private void loadViewSchema(File viewSchemaFile) {
        String viewName = viewSchemaFile.getName();
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(viewSchemaFile));
            Map<String, Integer> viewSchema = new LinkedHashMap<String, Integer>();
            int colTabCnt = 0;
            String tableColName = null;
            while ((tableColName = bufferedReader.readLine()) != null) {
                viewSchema.put(tableColName, colTabCnt);
                colTabCnt++;
            }
            Utils.viewToSchema.put(viewName, viewSchema);
            bufferedReader.close();
        } catch (Exception e) {

        }
    }

    private void saveTableSchema(File colDefsFile) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(colDefsFile));
            List<ColumnDefinition> columnDefinitions = new ArrayList<ColumnDefinition>();
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                String parts[] = line.split(",");
                ColumnDefinition columnDefinition = new ColumnDefinition();
                columnDefinition.setColumnName(parts[0]);
                ColDataType colDataType = new ColDataType();
                colDataType.setDataType(parts[1]);
                columnDefinition.setColDataType(colDataType);
                String tableColName = colDefsFile.getName() + "." + columnDefinition.getColumnName();
                Utils.colToColDef.put(tableColName, columnDefinition);
                Utils.colToColDef.put(tableColName, columnDefinition);
                columnDefinitions.add(columnDefinition);
            }
            Utils.nameToColDefs.put(colDefsFile.getName(), columnDefinitions);
            bufferedReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
