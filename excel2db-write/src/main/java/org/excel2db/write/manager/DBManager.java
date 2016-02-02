package org.excel2db.write.manager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.excel2db.write.util.TypeEnum;

/**
 * 生成ndb文件管理
 * 
 * @author ksfzhaohui
 * 
 */
public class DBManager {

	private final static Logger logger = Logger.getLogger(DBManager.class);

	/** 生成的二进制文件后缀 **/
	private static final String FILE_SUFFIX = ".ndb";

	/** header的长度 **/
	private static final int HEADER_LENGTH = 5 * 4;

	private Map<String, List<String>> columnNameMap;
	private Map<String, List<TypeEnum>> columnTypeMap;
	private Map<String, List<List<String>>> dataMap;

	public DBManager(ExcelManager excelManager) {
		columnNameMap = excelManager.getColumnNameMap();
		columnTypeMap = excelManager.getColumnTypeMap();
		dataMap = excelManager.getDataMap();
	}

	public void writeDB(String path) {
		logger.info("start write db file....");
		FileChannel fc = null;
		try {
			Set<String> keys = columnNameMap.keySet();
			for (String key : keys) {
				List<String> columnNames = columnNameMap.get(key);
				List<TypeEnum> typeEnums = columnTypeMap.get(key);
				List<List<String>> datas = dataMap.get(key);

				ByteBuffer headerBuffer = getHeaderBuffer(datas.size(),
						columnNames.size());
				ByteBuffer columnNameBuffer = getColumnNameBuffer(columnNames);
				ByteBuffer columnTypeBuffer = getColumnTypeBuffer(typeEnums);
				ByteBuffer dataBuffer = getDataBuffer(datas, typeEnums);

				headerBuffer.putInt(columnNameBuffer.limit());
				headerBuffer.putInt(columnTypeBuffer.limit());
				headerBuffer.putInt(dataBuffer.limit());

				fc = new FileOutputStream(path + key + FILE_SUFFIX)
						.getChannel();

				headerBuffer.flip();
				columnNameBuffer.flip();
				columnTypeBuffer.flip();
				dataBuffer.flip();

				fc.write(headerBuffer);
				fc.write(columnNameBuffer);
				fc.write(columnTypeBuffer);
				fc.write(dataBuffer);
			}
			logger.info("end write db file....");
		} catch (Exception e) {
			logger.error("writeDB error", e);
		} finally {
			if (fc != null) {
				try {
					fc.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * 头文件buff
	 * 
	 * @param dataSize
	 * @param columnSize
	 * @return
	 */
	private ByteBuffer getHeaderBuffer(int dataSize, int columnSize) {
		ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_LENGTH);
		headerBuffer.putInt(dataSize);
		headerBuffer.putInt(columnSize);
		return headerBuffer;
	}

	/**
	 * 字段名称buff
	 * 
	 * @param columnNames
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private ByteBuffer getColumnNameBuffer(List<String> columnNames)
			throws UnsupportedEncodingException {
		List<ByteBuffer> buffList = new ArrayList<ByteBuffer>();
		int length = 0;
		for (String columnName : columnNames) {
			ByteBuffer buffer = stringBuff(columnName);
			buffList.add(buffer);
			length += buffer.limit();
		}
		ByteBuffer columnBuff = ByteBuffer.allocate(length);
		for (ByteBuffer buffer : buffList) {
			columnBuff.put(buffer);
		}
		return columnBuff;
	}

	/**
	 * 获取字段类型buff
	 * 
	 * @param typeEnums
	 * @return
	 */
	private ByteBuffer getColumnTypeBuffer(List<TypeEnum> typeEnums) {
		ByteBuffer buffer = ByteBuffer.allocate(typeEnums.size());
		for (TypeEnum type : typeEnums) {
			buffer.put(type.value());
		}
		return buffer;
	}

	private ByteBuffer getDataBuffer(List<List<String>> datas,
			List<TypeEnum> typeEnums) throws UnsupportedEncodingException {
		List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
		for (List<String> data : datas) {
			int length = 0;
			for (int i = 0; i < data.size(); i++) {
				TypeEnum type = typeEnums.get(i);
				if (type == TypeEnum.STRING) {
					ByteBuffer buffer = stringBuff(data.get(i));
					length += buffer.limit();
				} else {
					length += TypeEnum.size(type);
				}
			}

			ByteBuffer buffer = ByteBuffer.allocate(length);
			for (int i = 0; i < data.size(); i++) {
				TypeEnum type = typeEnums.get(i);
				if (type == TypeEnum.INT) {
					buffer.putInt(Integer.valueOf(getInitValue(data.get(i))));
				} else if (type == TypeEnum.FLOAT) {
					buffer.putFloat(Float.valueOf(getInitValue(data.get(i))));
				} else if (type == TypeEnum.LONG) {
					buffer.putLong(Long.valueOf(getInitValue(data.get(i))));
				} else if (type == TypeEnum.STRING) {
					buffer.put(stringBuff(data.get(i)));
				} else {
					throw new RuntimeException("error type:" + type
							+ "support:int,float,long,string");
				}
			}

			buffer.flip();
			bufferList.add(buffer);
		}
		int allLength = 0;
		for (ByteBuffer buffer : bufferList) {
			allLength += buffer.limit();
		}
		ByteBuffer allBuffer = ByteBuffer.allocate(allLength);
		for (ByteBuffer buffer : bufferList) {
			allBuffer.put(buffer);
		}
		return allBuffer;
	}

	private ByteBuffer stringBuff(String str)
			throws UnsupportedEncodingException {
		byte bytes[] = str.getBytes("UTF-8");
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
		buffer.putInt(bytes.length);
		buffer.put(bytes);
		buffer.flip();
		return buffer;
	}

	/**
	 * 获取非string类型的初始值
	 * 
	 * @param value
	 * @return
	 */
	private String getInitValue(String value) {
		if (value.equals("")) {
			return "0";
		}
		return value;
	}
}
