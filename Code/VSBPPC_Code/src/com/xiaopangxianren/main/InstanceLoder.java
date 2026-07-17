package com.xiaopangxianren.main;

import com.xiaopangxianren.classes.*;
import com.xiaopangxianren.solver.VSBPPTW_Solver;
import com.xiaopangxianren.utils.TimeUtil;
import ilog.concert.IloException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstanceLoder {
    // 定义论文中不同的箱子类型
    public static List<Integer>[] paperBinTypeCapacity = new List[]{
            Arrays.asList(100, 120, 150), // Set1_Bin_3
            Arrays.asList(60, 80, 100, 120, 150), // Set1_Bin_5
            Arrays.asList(70, 100, 130, 160, 190, 220, 250), // Set2_Bin_Linear
            Arrays.asList(70, 100, 130, 160, 190, 220, 250), // Set2_Bin_Concavity
            Arrays.asList(70, 100, 130, 160, 190, 220, 250) // Set2_Bin_Convexity
    };

    public static List<Integer>[] paperBinTypeCost = new List[]{
            Arrays.asList(100, 120, 150), // Set1_Bin_3
            Arrays.asList(60, 80, 100, 120, 150), // Set1_Bin_5
            Arrays.asList(70, 100, 130, 160, 190, 220, 250), // Set2_Bin_Linear
            Arrays.asList(84, 100, 115, 127, 138, 149, 159), // Set2_Bin_Concavity
            Arrays.asList(59, 100, 149, 203, 262, 327, 396) // Set2_Bin_Convexity
    };

    // set-1 and set-2 的时间限制（单位：毫秒）
    public static Map<Integer, Integer> set1AndSet2SolverTime = new HashMap<Integer, Integer>() {{
        put(100, 200);
        put(200, 1500);
        put(500, 23250);
        put(1000, 190710);
    }};


    public static Instance readInstance(String path) throws FileNotFoundException {
        Parameter.isTimeWindowConstraint = true;

        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + path);
        }

        Scanner scanner = new Scanner(file);
        Instance instance = new Instance();
        ArrayList<BinType> binTypes = new ArrayList<>();
        ArrayList<Item> items = new ArrayList<>();

        // 1. 读取第一行：文件名及其后的配置参数
        if (!scanner.hasNext()) {
            scanner.close();
            throw new RuntimeException("文件为空！");
        }

        instance.name = scanner.next();

        scanner.nextLine();

        if (!scanner.hasNextInt()) {
        }
        int binTypeNum = scanner.nextInt();

        for (int i = 0; i < binTypeNum; i++) {
            int capacity = scanner.nextInt();
            int cost = scanner.nextInt();
            binTypes.add(new BinType(i, capacity, cost));
        }

        int itemNum = scanner.nextInt();

        for (int i = 0; i < itemNum; i++) {
            int volume = scanner.nextInt();
            int startTime = scanner.nextInt();
            int endTime = scanner.nextInt();
            items.add(new Item(i, i, volume, startTime, endTime));
        }

        scanner.close();

        instance.items = items;
        instance.binTypes = binTypes;

        return instance;
    }

    public static Instance readInstance(String filePath, InstanceBinType type) throws IOException {
        Parameter.isTimeWindowConstraint = false;
        Path p = Paths.get(filePath);
        String instanceName = p.getFileName().toString();

        if (instanceName.endsWith(".txt")) {
            instanceName = instanceName.substring(0, instanceName.length() - 4);
        }

        List<String> rawLines = Files.readAllLines(p);
        List<String> lines = new ArrayList<>();

        for (String line : rawLines) {
            if (line != null && !line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        int n = Integer.parseInt(lines.get(0));
        if (lines.size() < n + 1) {
            throw new IllegalArgumentException("文件行数不足，期望至少 " + (n + 1) + " 行，实际 " + lines.size() + " 行");
        }

        List<Item> items = new ArrayList<>(n);

        // 第一遍：读 item
        for (int i = 1; i <= n; i++) {
            String[] parts = lines.get(i).split("\\s+");
            int id = i - 1;
            int real_id = Integer.parseInt(parts[0]);
            int volume = Integer.parseInt(parts[1]);

            items.add(new Item(id, real_id, id, volume));
        }

        BitSet[] conflictMatrix = new BitSet[n];
        for (int i = 0; i < n; i++) {
            conflictMatrix[i] = new BitSet(n);
        }

        // 第二遍：读 conflict
        for (int i = 1; i <= n; i++) {
            String[] parts = lines.get(i).split("\\s+");
            int id = Integer.parseInt(parts[0]) - 1;

            for (int j = 2; j < parts.length; j++) {
                int cid = Integer.parseInt(parts[j]) - 1;
                conflictMatrix[id].set(cid);
                conflictMatrix[cid].set(id);
            }
        }

        List<BinType> binTypes = new ArrayList<>();
        for (int i = 0; i < paperBinTypeCapacity[type.ordinal()].size(); i++) {
            binTypes.add(new BinType(i, paperBinTypeCapacity[type.ordinal()].get(i), paperBinTypeCost[type.ordinal()].get(i)));
        }

        return new Instance(instanceName, binTypes, items, conflictMatrix);
    }

    public static void saveToCSV(String filename, DataLogging data, String savePath) {
        int totalSize = data.totalTime.size();
        if (totalSize == 0) return;

        int solutionOptCnt = 0;
        int rootOptCnt = 0;
        long maxTotalTime = -1;
        double maxGap = -1.0;

        double sumTotalTime = 0, sumRmpTime = 0, sumPricingTime = 0, sumDivingTime = 0;
        double sumGap = 0;
        double sumAvgFindPatternEnuTime = 0;
        double sumAvgPatternEnuTime = 0;

        for (int i = 0; i < totalSize; i++) {
            if (data.isOpt.get(i)) solutionOptCnt++;
            if (data.rootEnu_isOpt.get(i)) rootOptCnt++;

            maxTotalTime = Math.max(maxTotalTime, data.totalTime.get(i));
            maxGap = Math.max(maxGap, data.gap.get(i));

            sumTotalTime += data.totalTime.get(i);
            sumRmpTime += data.avgRmpTime.get(i);
            sumPricingTime += data.avgPricingTime.get(i);
            sumDivingTime += data.divingTime.get(i);
            sumGap += data.gap.get(i);

            sumAvgFindPatternEnuTime += data.avgFindPatternEnuTime.get(i);
            sumAvgPatternEnuTime += data.avgPatternEnuTime.get(i);
        }

        try (FileWriter out = new FileWriter(savePath + "/" + filename + "_results.csv");
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {

            printer.printRecord("Dataset: " + filename);
            printer.printRecord("Total Samples: " + totalSize);
            printer.printRecord("Optimal Solutions (isOpt): " + solutionOptCnt + "/" + totalSize);
            printer.printRecord("Enumerative Solution: " + rootOptCnt + "/" + totalSize);
            printer.printRecord("Maximum Gap: " + maxGap);
            printer.printRecord("Average Gap: " + (sumGap / totalSize));
            printer.printRecord("Maximum Total Time (ms): " + maxTotalTime);
            printer.printRecord("Average Total Time (ms): " + (sumTotalTime / totalSize));
            printer.printRecord("Average RMP Time (ms): " + (sumRmpTime / totalSize));
            printer.printRecord("Average Pricing Time (ms): " + (sumPricingTime / totalSize));
            printer.printRecord("Average Diving Time (ms): " + (sumDivingTime / totalSize));
            printer.printRecord("Average Find Pattern Time (ms): " + (sumAvgFindPatternEnuTime / totalSize));
            printer.printRecord("Average Pattern Enu Time (ms): " + (sumAvgPatternEnuTime / totalSize));

            printer.println();
            printer.println();

            printer.printRecord(
                    "instance", "UB", "LB", "Gap", "IsOpt", "RootEnu_IsOpt",
                    "GenNodeCnt", "ExpNodeCnt", "RmpCnt", "PricingCnt", "ExpDivingNodeCnt", "PatternEnuCnt",
                    "TotalTime", "RmpTime", "PricingTime", "DivingTime", "FindPatternEnuTime", "PatternEnuTime",
                    "AvgRmpTime", "AvgPricingTime", "AvgFindPatternEnuTime", "AvgPatternEnuTime"
            );

            for (int i = 0; i < totalSize; i++) {
                printer.printRecord(
                        data.fileNames.get(i),
                        data.UB.get(i),
                        data.LB.get(i),
                        data.gap.get(i),
                        data.isOpt.get(i),
                        data.rootEnu_isOpt.get(i),
                        data.generatedNodeCnt.get(i),
                        data.exploredNodeCnt.get(i),
                        data.rmpCnt.get(i),
                        data.pricingCnt.get(i),
                        data.exploredDivingNodeCnt.get(i),
                        data.patternEnuCnt.get(i),
                        data.totalTime.get(i),
                        data.rmpTime.get(i),
                        data.pricingTime.get(i),
                        data.divingTime.get(i),
                        data.findPatternEnuTime.get(i),
                        data.patternEnuTime.get(i),
                        data.avgRmpTime.get(i),
                        data.avgPricingTime.get(i),
                        data.avgFindPatternEnuTime.get(i),
                        data.avgPatternEnuTime.get(i)
                );
            }

            System.out.println("Data successfully saved to: " + savePath + "/" + filename + "_results.csv");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getStringList(String path) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(path));
        List<String> stringsList = new ArrayList<>();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (!line.isEmpty()) {
                stringsList.add(line);
            }
        }
        sc.close();
        return stringsList;
    }

    private static void runningAllInstances(InstanceBinType type, String fileListPath, String savePath) throws IOException, IloException {
        Path p = Paths.get(fileListPath);
        String instanceType = p.getFileName().toString();
        Path prefix = p.getParent().getParent();
        List<String> instanceFiles = getStringList(fileListPath);

        for (String fileName : instanceFiles) {
            Instance instance = null;
            DataLogging dataLogging = new DataLogging();
            if (type == InstanceBinType.Set3_Opt_With_Time_Window || type == InstanceBinType.Set3_Hem_With_Time_Window) {
                instance = readInstance(prefix + "\\" + instanceType + "\\" + fileName);
            } else {
                instance = readInstance(prefix + "\\" + instanceType + "\\" + fileName, type);
            }
            System.out.println(instance.name);
            System.out.println("type = " + instance.type);
            System.out.println("optCost = " + instance.optCost);
            System.out.println("binTypes size = " + instance.binTypes.size());
            System.out.println("items size = " + instance.items.size());

            // 记录开始时间
            TimeUtil.startTime = System.currentTimeMillis();
            VSBPPTW_Solver solver = new VSBPPTW_Solver(instance.binTypes, instance.items, instance);
            solver.solve();
            // 记录数据
            dataLogging.add_data(instance.name, solver.bestSolution);

            saveToCSV(fileName, dataLogging, savePath);
        }
    }

    public static void runningAllOptInstances(String optInstanceDirPath, String savePath) throws IOException, IloException {
        TimeUtil.TimeLimit = 600000; // 10分钟
        runningAllInstances(InstanceBinType.Set3_Opt_With_Time_Window, optInstanceDirPath, savePath);
    }

    public static void runningAllHemInstances(String hemInstanceDirPath, String savePath) throws IOException, IloException {
        TimeUtil.TimeLimit = 600000; // 10分钟
        runningAllInstances(InstanceBinType.Set3_Hem_With_Time_Window, hemInstanceDirPath, savePath);
    }

    public static void runningAllSet1Instances(String set1InstanceDirPath, String savePath) throws IOException, IloException {
        List<String> instanceNameList = getStringList(set1InstanceDirPath);
        File file = new File(set1InstanceDirPath);
        String parentDir = file.getParent();

        for (String instanceName : instanceNameList) {
            Pattern pattern = Pattern.compile("with(\\d+)Items");
            Matcher matcher = pattern.matcher(instanceName);

            if (!matcher.find()) {
                throw new RuntimeException("Unable to extract item quantity from instance name: " + instanceName);
            }
            int itemNum = Integer.parseInt(matcher.group(1));
            // 根据物品数量设置时间限制
            TimeUtil.TimeLimit = set1AndSet2SolverTime.get(itemNum);

            String dirPath = savePath + "/" + instanceName + "_3";
            // 创建文件对象
            File directory = new File(dirPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            runningAllInstances(InstanceBinType.Set1_Bin_3, parentDir + "/" + instanceName,
                    dirPath);

            String dirPath1 = savePath + "/" + instanceName + "_5";
            // 创建文件对象
            File directory1 = new File(dirPath1);
            if (!directory1.exists()) {
                directory1.mkdirs();
            }
            runningAllInstances(InstanceBinType.Set1_Bin_5, parentDir + "/" + instanceName,
                    dirPath1);
        }
    }

    public static void runningAllSet2Instances(String set2InstanceDirPath, String savePath) throws IOException, IloException {
        List<String> instanceNameList = getStringList(set2InstanceDirPath);

        File file = new File(set2InstanceDirPath);
        String parentDir = file.getParent();

        for (String instanceName : instanceNameList) {
            Pattern pattern = Pattern.compile("with(\\d+)Items");
            Matcher matcher = pattern.matcher(instanceName);

            if (!matcher.find()) {
                throw new RuntimeException("Unable to extract item quantity from instance name: " + instanceName);
            }
            int itemNum = Integer.parseInt(matcher.group(1));
            TimeUtil.TimeLimit = set1AndSet2SolverTime.get(itemNum);

            String dirPath = savePath + "/" + instanceName + "_Linear";
            // 创建文件对象
            File directory = new File(dirPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            runningAllInstances(InstanceBinType.Set2_Bin_Linear, parentDir + "/" + instanceName,
                    dirPath);

            String dirPath1 = savePath + "/" + instanceName + "_Convexity";
            // 创建文件对象
            File directory1 = new File(dirPath1);
            if (!directory1.exists()) {
                directory1.mkdirs();
            }
            runningAllInstances(InstanceBinType.Set2_Bin_Convexity, parentDir + "/" + instanceName,
                    dirPath1);

            String dirPath2 = savePath + "/" + instanceName + "_Concavity";
            // 创建文件对象
            File directory2 = new File(dirPath2);
            if (!directory2.exists()) {
                directory2.mkdirs();
            }
            runningAllInstances(InstanceBinType.Set2_Bin_Concavity, parentDir + "/" + instanceName,
                    dirPath2);
        }
    }

    public static void main(String[] args) throws IOException, IloException {
        // Opt
        runningAllOptInstances("../../Instances/Instance_1/ReadFiles/opt",
                "../../Results/all/opt");

        // Hem
        runningAllHemInstances("../../Instances/Instance_1/ReadFiles/hem",
                "../../Results/all/hem");

        // Set1
        runningAllSet1Instances("../../Instances/Instance_2/ReadFiles/ReadFilesSet1.txt",
                "../../Results/all/set-1");

        // Set2
        runningAllSet2Instances("../../Instances/Instance_2/ReadFiles/ReadFilesSet2.txt",
                "../../Results/all/set-2");
    }

}

