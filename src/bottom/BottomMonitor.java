package bottom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 *  负责监视和管理底层各项数据
 */
public class BottomMonitor {
    private HashMap<Integer,TaskState> stateMap;
    private HashMap<Integer,List<Task>> arriveMap;
    private HashSet<Integer> runningSet;
    private boolean[] resourceUse = new boolean[Constant.MAX_RESOURCE];
    private int readCnt;
    private int writeCnt;
    private int cpuNumber;
    private int[] cpuState;
    private int timeTick;

    /**
     * 从文件中读取指定内容，初始化预定好的任务队列
     * @param filename
     * @param cpuNumber
     * @throws IOException
     */
    public BottomMonitor(String filename,int cpuNumber) throws IOException {
        this.cpuNumber = cpuNumber;
        stateMap = new HashMap<>();
        arriveMap = new HashMap<>();
        cpuState = new int[cpuNumber];
        runningSet = new HashSet<>();
        List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
        lines.remove(0);
        lines.stream().forEach(t->{
            String[] sp = t.split(",");
            if(sp.length != 4) {
                return;
            }
            int tid = Integer.valueOf(sp[0]);
            int cpuTime = Integer.valueOf(sp[2]);
            int arriveTime = Integer.valueOf(sp[1]);
            String[] resStr = sp[3].trim().split(" ");
            int[] res = new int[resStr.length];
            int[] resT = new int[resStr.length];
            for(int i = 0 ; i < res.length ; i++){
                res[i] = Integer.valueOf(resStr[i]);
                resT[i] = Integer.valueOf(resStr[i]);
            }
            TaskState taskState = new TaskState(tid,cpuTime,res,arriveTime);
            stateMap.put(tid,taskState);
            Task task = new Task(tid,cpuTime,resT);
            List<Task> list = arriveMap.get(arriveTime);
            if(list!=null)  list.add(task);
            else{
                list = new ArrayList<>();
                list.add(task);
                arriveMap.put(arriveTime,list);
            }

        });
    }

    /**
     * 时间序列递增
     */
    public void increment(){
        timeTick++;
    }


    /**
     * 获得时间片
     * @return
     */
    public int getTimeTick(){
        return timeTick;
    }

    /**
     * 获得cpu数量
     * @return
     */
    public  int getCpuNumber(){
        return cpuNumber;
    }


    /**
     * 获得当前时间片到达的任务
     * @return
     */
    public Task[]  getTaskArrived(){
        if(arriveMap.containsKey(timeTick)){
            List<Task>  list = arriveMap.get(timeTick);
            Task[] tasks = new Task[list.size()];
            tasks = list.toArray(tasks);
            return tasks;
        }else{
            return new Task[0];
        }
    }


    /**
     * 底层运行cpu
     * @param cpuOperate
     * @throws Exception
     */
    public void runCpu(int[] cpuOperate) throws Exception {
        for(int i = 0 ; i < cpuNumber ; i++){
            if(cpuOperate[i]!=cpuState[i] && cpuState[i]!=Constant.CPU_FREE){
                int lastTid =  cpuState[i];
                runningSet.remove(lastTid);
                int[] res = stateMap.get(lastTid).resource;
                for(int r : res) resourceUse[r] = false;
            }
        }

        for(int i = 0 ; i < cpuNumber ; i++){
            if(cpuOperate[i]!=cpuState[i] && cpuOperate[i]!=Constant.CPU_FREE){
                int newTid =  cpuOperate[i];
                if(runningSet.contains(newTid)){
                    // TODO add ERROR
                    throw new Exception("TaskDoubleRun");
                }
                runningSet.add(newTid);
                int[] res = stateMap.get(newTid).resource;

                for(int r : res) {
                    if(resourceUse[r]){
                        // TODO add ERROR
                        throw new Exception("resourceDoubleUse");
                    }
                    resourceUse[r] = true;
                }
                cpuState[i] = cpuOperate[i];
            }
        }

        for(int i = 0 ; i < cpuNumber ; i++) {
            int runningTid = cpuState[i];
            if(runningTid == Constant.CPU_FREE)  continue;
            TaskState taskState = stateMap.get(runningTid);
            if(!taskState.isArrived(timeTick)){
                // TODO add ERROR
                throw new Exception("no this task");
            }

            if (taskState.isFinish()){
                continue;
            }

            taskState.leftCpuTime--;
            if(taskState.leftCpuTime == 0){
                taskState.finishTime = timeTick;
            }
        }

    }
}