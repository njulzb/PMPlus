package work;

import bottom.BottomMonitor;
import bottom.BottomService;
import bottom.Constant;
import bottom.Task;
import main.Schedule;

import java.io.IOException;

/**
 *
 * 注意：请将此类名改为 S+你的学号   eg: S161250001
 * 提交时只用提交此类和说明文档
 *
 * 在实现过程中不得声明新的存储空间（不得使用new关键字，反射和java集合类）
 * 所有新声明的类变量必须为final类型
 * 不得创建新的辅助类
 *
 * 可以生成局部变量
 * 可以实现新的私有函数
 *
 * 可用接口说明:
 *
 * 获得当前的时间片
 * int getTimeTick()
 *
 * 获得cpu数目
 * int getCpuNumber()
 *
 * 对自由内存的读操作  offset 为索引偏移量， 返回位置为offset中存储的byte值
 * byte readFreeMemory(int offset)
 *
 * 对自由内存的写操作  offset 为索引偏移量， 将x写入位置为offset的内存中
 * void writeFreeMemory(int offset, byte x)
 *
 */
public class S161250063 extends Schedule{

    //资源位图基址
    private static final int resource_base = 0;
    //资源位图限长
    private static final int resource_length = Constant.MAX_RESOURCE;
    //pcb分配表基址
    private static final int pcb_table_base = resource_base + resource_length;
    //pcb分配表长度
    private static final int pcb_table_length = 1000 * 4;

    //pcb存储空间分配指针 pcb_mem_base <= pcb_cursor <= pcb_mem_base + pcb_mem_length
    private static final int pcb_cursor = pcb_table_base + pcb_table_length;

    private static final int cpu_run_last_time = pcb_cursor + 4;
    private static final int cpu_mem_base = cpu_run_last_time + 4;
    private static final int cpu_mem_length = 5 * 4;

    private static final int min_task_id = cpu_mem_base + cpu_mem_length;
    private static final int max_task_id = min_task_id + 4;

    //pcb存储基址
    private static final int pcb_mem_base = max_task_id + 4;
    //pcb存储限长
    private static final int pcb_mem_length = 1000 * 20;


    private static final int pcb_tid = 0;
    private static final int pcb_arrivedTime = 4;
    private static final int pcb_cpuTime = 8;
    private static final int pcb_leftTime = 12;
    private static final int pcb_rsLength = 16;
    private static final int pcb_resourceList = 20;



    @Override
    public void ProcessSchedule(Task[] arrivedTask, int[] cpuOperate) {
        //记录新加入的task
        if (arrivedTask!=null && arrivedTask.length!=0){
            for (Task task : arrivedTask) {
                this.recordTask(task);
            }
        }


        //调度
        this.initResource();//释放所占用的资源

        int cpuNumber = this.getCpuNumber();
        //恢复上次的cpu状态
        int cpuRunLastTime = this.readInteger(cpu_run_last_time);
        boolean ifCPURunLastTime = (cpuRunLastTime!=0);
        for (int cpuNo = 0; ifCPURunLastTime && cpuNo < cpuOperate.length; cpuNo++) {
            int tid = this.getCpuMem(cpuNo);
            if (this.isTaskExists(tid) && !this.isTaskDone(tid)){
                cpuOperate[cpuNo] = tid;
                cpuNumber--;
                //run cpu
                this.takeUpAllNeededResource(cpuOperate[cpuNo]);
                countDownTask(cpuOperate[cpuNo]);
                this.setCpuMem(cpuNo,cpuOperate[cpuNo]);
            }
            else {
                cpuOperate[cpuNo] = 0;
            }

        }
        int maxTaskID = this.readInteger(max_task_id);
        int minTaskID = this.getMinTaskID();
        for (int taskID = minTaskID; taskID <= maxTaskID && cpuNumber>0 ; taskID++) {
            boolean ifContains = false;
            for (int cpuTask : cpuOperate) {
                if (taskID==cpuTask){
                    ifContains = true;
                }
            }
            if (ifContains){
                continue;
            }
            if (!this.isTaskExists(taskID)){
                continue;
            }
            if (this.isTaskDone(taskID)){
                continue;
            }
            if (!this.ifResourceCoversTask(taskID)){
                continue;
            }
            for (int cpuNo = 0; cpuNo < cpuOperate.length; cpuNo++) {
                if (cpuOperate[cpuNo] == 0) {
                    cpuOperate[cpuNo] = taskID;
                    cpuNumber--;
                    this.takeUpAllNeededResource(cpuOperate[cpuNo]);
                    countDownTask(cpuOperate[cpuNo]);
                    this.setCpuMem(cpuNo,cpuOperate[cpuNo]);
                    break;
                }
            }
        }

        int cpuLastTime = 0;
        for (int taskID : cpuOperate) {
            if (taskID!=0){
                cpuLastTime = 1;
                break;
            }
        }
        this.writeInteger(cpu_run_last_time,cpuLastTime);






    }

    private int getCpuMem(int cpuNo){
        int cpuMem = this.readInteger(cpu_mem_base + cpuNo * 4);
        return cpuMem;
    }
    private void setCpuMem(int cpuNo , int taskID){
        this.writeInteger(cpu_mem_base + cpuNo * 4, taskID);
    }

    private int getMinTaskID(){
        int minTaskID = this.readInteger(min_task_id);
        int maxTaskID = this.readInteger(max_task_id);
        while (!this.isTaskExists(minTaskID) || this.isTaskDone(minTaskID)){
            if (minTaskID>=maxTaskID){
                break;
            }
            minTaskID++;
        }
        this.writeInteger(min_task_id,minTaskID);
        return minTaskID;
    }


    private void recordTask(Task task){
        int maxTaskID = this.readInteger(max_task_id);
        maxTaskID = (maxTaskID>task.tid)?maxTaskID:task.tid;
        this.writeInteger(max_task_id,maxTaskID);

        int pcbStart = this.readInteger(pcb_cursor);//得到新的存储地址
        if (pcbStart<pcb_mem_base || pcbStart> pcb_mem_base + pcb_mem_length){
            pcbStart = pcb_mem_base;
        }
        this.writeInteger(pcb_table_base + task.tid*4 ,pcbStart);//将地址存进pcb分配表
        this.writeInteger(pcbStart + pcb_tid , task.tid);
        this.writeInteger(pcbStart + pcb_arrivedTime , this.getTimeTick());
        this.writeInteger(pcbStart + pcb_cpuTime , task.cpuTime );
        this.writeInteger(pcbStart + pcb_leftTime , task.cpuTime);
        this.writeInteger(pcbStart + pcb_rsLength , task.resource.length);

        int resourceListBase = this.getResourceListBase(task.tid);
        for (int i = 0; i < task.resource.length; i++) {
            this.writeInteger(resourceListBase + i*4 , task.resource[i]);
        }

        this.writeInteger(pcb_cursor,resourceListBase + task.resource.length * 4);//更新cursor地址
        int test = this.readInteger(pcb_cursor);

    }


    private boolean isTaskExists(int tid){
        int pcbStart = this.getPCBStartAddr(tid);
        return (pcbStart >= pcb_mem_base) && (pcbStart <= (pcb_mem_base + pcb_mem_length));
    }

    /**
     * 返回tid所示pcb起始地址
     * @param tid
     * @return
     */
    private int getPCBStartAddr(int tid){
        int pcbStart = this.readInteger(pcb_table_base + tid * 4);
        return pcbStart;
    }

    private int getPcb_arrivedTime(int tid){
        int pcbStart = this.getPCBStartAddr(tid);
        int arrivedTime = this.readInteger(pcbStart + pcb_arrivedTime);
        return arrivedTime;
    }
    private int getCpuTime(int tid){
        int pcbStart = this.getPCBStartAddr(tid);
        int cpuTime = this.readInteger(pcbStart + pcb_cpuTime);
        return cpuTime;
    }
    private void  setLeftTime(int tid , int newLeftTime){
        int pcbStart = this.getPCBStartAddr(tid);
        this.writeInteger(pcbStart + pcb_leftTime , newLeftTime);
    }
    private int getLeftTime(int tid){
        int pcbStart = this.getPCBStartAddr(tid);
        int leftTime = this.readInteger(pcbStart + pcb_leftTime);
        return leftTime;
    }
    private int getResourceListLength(int tid){
        int pcbStart = this.getPCBStartAddr(tid);
        int resourceListLength = this.readInteger(pcbStart + pcb_rsLength);
        return resourceListLength;
    }
    private int getResourceListBase(int tid){
        int pcbStart = this.getPCBStartAddr(tid);
        return pcbStart + pcb_resourceList;
    }


    private boolean ifResourceCoversTask(int tid){
        int resourceListBase = this.getResourceListBase(tid);
        int length = this.getResourceListLength(tid);
        for (int i = 0; i < length; i++) {
            int resourceID = this.readInteger(resourceListBase + i*4);
            if (!this.isResourceAvailable(resourceID)){
                //if resource is not available
                return false;
            }
        }
        return true;
    }

    private void takeUpAllNeededResource(int tid){
        int resourceListBase = this.getResourceListBase(tid);
        int length = this.getResourceListLength(tid);
        for (int i = 0; i < length; i++) {
            int resourceID = this.readInteger(resourceListBase + i*4);
            this.takeUpResource(resourceID);
        }
    }

    private boolean isTaskDone(int tid){
        int leftTime = this.getLeftTime(tid);
        return leftTime==0;
    }

    private void countDownTask(int tid){
        int leftTime = this.getLeftTime(tid);
        leftTime--;
        this.setLeftTime(tid,leftTime);
    }

    /**
     * 写入一个int
     * @param base
     * @param value
     */
    private void writeInteger(int base,int value){
        this.writeFreeMemory(base + 0, (byte)((value>>0)&0xff));
        this.writeFreeMemory(base + 1, (byte)((value>>8)&0xff));
        this.writeFreeMemory(base + 2, (byte)((value>>16)&0xff));
        this.writeFreeMemory(base + 3, (byte)((value>>24)&0xff));
//        int test = this.readInteger(base);
//        System.out.println("base="+base +"\tinput="+value+ "\toutput="+test);

    }

    /**
     * 读取一个int
     * @param base
     * @return
     */
    private int readInteger(int base){
        int result = 0;
        result += this.readFreeMemory(base)&0xff;
        result += (this.readFreeMemory(base+1)&0xff)<<8;
        result += (this.readFreeMemory(base + 2)&0xff)<<16;
        result += (this.readFreeMemory(base + 3)&0xff)<<24;
        return result;
    }
    /**
     *
     * @param resourceID        1<=id<=128
     * @return
     */
    private boolean isResourceAvailable(int resourceID){
        byte flag = this.readFreeMemory(resource_base + resourceID - 1);
        return flag==1;
    }



    /**
     * 占用资源
     * @param resourceID
     */
    private void takeUpResource(int resourceID){
        this.writeFreeMemory(resource_base + resourceID - 1,(byte)0);
    }


    /**
     * 释放资源
     * @param resourceID
     */
    private void releaseResource(int resourceID){
        this.writeFreeMemory(resource_base + resourceID -1 ,(byte)1);
    }

    /**
     * 初始化资源
     */
    private void initResource(){
        for (int i = 0; i < resource_length; i++) {
            this.writeFreeMemory(i,(byte)1);
        }
    }



    /**
     * 执行主函数 用于debug
     * 里面的内容可随意修改
     * 你可以在这里进行对自己的策略进行测试，如果不喜欢这种测试方式，可以直接删除main函数
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // 定义cpu的数量
        int cpuNumber = 3;
        // 定义测试文件
//        String filename = "src/testFile/textSample.txt";
        String filename = "src/testFile/rand_8.csv";

        BottomMonitor bottomMonitor = new BottomMonitor(filename,cpuNumber);
        BottomService bottomService = new BottomService(bottomMonitor);
        Schedule schedule =  new S161250063();
        schedule.setBottomService(bottomService);

        //外部调用实现类
        for(int i = 0; i < Constant.ITER_NUM ; i++){
            Task[] tasks = bottomMonitor.getTaskArrived();
            int[] cpuOperate = new int[cpuNumber];

            // 结果返回给cpuOperate
            schedule.ProcessSchedule(tasks,cpuOperate);

            try {
                bottomService.runCpu(cpuOperate);
            } catch (Exception e) {
                System.out.println("Fail: "+e.getMessage());
                e.printStackTrace();
                return;
            }
            bottomMonitor.increment();
        }

        //打印统计结果
        bottomMonitor.printStatistics();
        System.out.println();

        //打印任务队列
        bottomMonitor.printTaskArrayLog();
        System.out.println();

        //打印cpu日志
        bottomMonitor.printCpuLog();


        if(!bottomMonitor.isAllTaskFinish()){
            System.out.println(" Fail: At least one task has not been completed! ");
        }
    }

}
