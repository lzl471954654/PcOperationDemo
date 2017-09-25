package pcMain;

import Utils.IntConvertUtils;
import beans.*;
import com.google.gson.Gson;
import com.sun.istack.internal.NotNull;
import pcOp.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class OpenService {
    private static Socket connSocket;
    private static OutputStream os;
    private static InputStream is;
    private static BufferedReader reader;
    private static Gson gson = new Gson();
    private static boolean loopFlag = true;

    public static void main(String[] args) {
        while (true) {
            start();
            try {
                loopFlag = true;
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private static void start() {
        try {
            connSocket = new Socket(Parameter.SERVER_IP, 10087);
            OutputStream outputStream = connSocket.getOutputStream();
            InputStream inputStream = connSocket.getInputStream();

            os = connSocket.getOutputStream();
            is = connSocket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));
            //向服务端发送消息，我要上线
            String username = System.getProperty("user.name");
            System.out.println(username);
            /*writer.println("|ONLINE|_" + "lzl471954654" + "_Test_" + Parameter.END_FLAG);
            writer.flush();*/
            //sendMsg("|ONLINE|_" + "tjoe" + "_tjoe_" + Parameter.END_FLAG);
            sendMsg("|ONLINE|_" + "PJW" + "_Test_" + Parameter.END_FLAG);
            String result = readString();
            System.out.println(result);
            if (StringUtils.startAndEnd(result)) {

                /**
                 * 下线
                 * 时间限制
                 * 离线
                 */

                loop();
            } else {  //上线失败
                System.out.println("上线失败，请重新上线。。。");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loop() {
        while (loopFlag) {
            try {
                //持续不断的读取服务端消息
                String op = readString();
                if (!op.endsWith(Parameter.END_FLAG))
                    continue;
                System.out.println("op is :" + op);
                Content content = StringUtils.getContent(op);

                if ((content.getContent() == null )| content.getHead() == null | content.getTail()==null){
                    System.out.println("命令错误!");
                    continue;
                }
                if (content.getHead().equals(Parameter.FILE_LIST_FLAG)) {
                    FileCommand command = gson.fromJson(content.getContent(), FileCommand.class);
                    fileOperation(command, content.getContent());
                    continue;
                }
                if (content.getHead().equals(Parameter.COMMAND)) {
                    Command command = gson.fromJson(content.getContent(), Command.class);
                    operation(command);
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (e instanceof SocketException) {
                    break;
                }
            }
        }
    }


    private static void sendMsg(@NotNull String s) {
        try {
            byte[] bytes = s.getBytes("UTF-8");
            os.write(IntConvertUtils.getIntegerBytes(bytes.length));
            os.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readString() {
        String s = "";
        try {
            int msgSize = 0;
            byte[] msgSizeBytes = new byte[4];
            int readSize = is.read(msgSizeBytes);
            System.out.println("readSIze is " + readSize);
            msgSize = IntConvertUtils.getIntegerByByteArray(msgSizeBytes);
            if (msgSize <= 0) {
                loopFlag = false;
                return "";
            }
            System.out.println("msgSize is " + msgSize);
            int i = 0;
            byte[] dataBytes = new byte[msgSize];
            while (i < msgSize) {
                dataBytes[i] = (byte) is.read();
                i++;
            }
            s = new String(dataBytes);
            System.out.println("msg is " + s);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s;
    }

    /**
     * 对电脑的操作
     */
    private static void operation(Command command) {
        int type = Integer.valueOf(command.getType());
        switch (type) {
            case -1:
                send2service();
                break;
            case 0://关机
                PcTools pcTools = new PcTools();
                pcTools.opencmd(21);
                // send2service(writer);
                break;
            case 1://取消关机
                PcTools pcTools1 = new PcTools();
                pcTools1.opencmd(23);
                break;

            case 2:
                PcScreen pcScreen = new PcScreen(command.getDescribe());
                if (command.getIsBack()) {
                    pcScreen.sendScreen(command.getDescribe(), os);
                } else {
                    pcScreen.shot();
                }
                break;

            case 3://鼠标操作
                String mouseinfo=command.getDescribe();
                MouseOpInfo mouseOp=gson.fromJson(mouseinfo,MouseOpInfo.class);
                PcMouse pcMouse=new PcMouse();
                if (mouseOp.isClick()){//点击了
                   if (mouseOp.isSingleClick()){//单击
                        pcMouse.singleClick();
                   }else if (mouseOp.isRightClick()){//右击
                        pcMouse.rightClick();
                   }else if (mouseOp.isDoubleClick()){//双击
                        pcMouse.doubleClick();
                   }
                }else{
                   //调用移动的方法

                    int x=(int)mouseOp.getX();
                    int y=(int)mouseOp.getY();
                    pcMouse.move(x,y);
                }
                break;
            case 4://获取磁盘详细信息
                PcDisk pcDisk = new PcDisk();
                if (command.getDescribe().isEmpty()) {
                    String diskString = gson.toJson(pcDisk.getDisk());
                    sendMsg(diskString + "_" + Parameter.END_FLAG);
                } else {
                    String dir = gson.toJson(pcDisk.getFileDirectory(command.getDescribe()));
                    sendMsg(dir + "_" + Parameter.END_FLAG);
                }
                break;
            case 7:
                PcSearch pcSearch = new PcSearch();
                pcSearch.voiceSearch(command.getDescribe());
                break;
            default:
                PcTools tool = new PcTools();
                if (type < 127) {
                    tool.opencmd(type % 100);
                } else {
                    tool.closecmd(type % 200);
                }

        }
    }

    private static void fileOperation(FileCommand command, String jsonSrc) {
        int type = Integer.valueOf(command.getType());
        switch (type) {
            case 20:
                acceptFile(command.getDescribe(), jsonSrc);
                break;
        }
    }

    private static void acceptFile(FileDescribe[] describes, String jsonSrc) {
        sendMsg(Parameter.FILE_READY + "_" + jsonSrc + "_" + Parameter.END_FLAG);
        for (FileDescribe describe : describes) {
            String fileName = describe.getFileName() + "." + describe.getFileType();
            Long fileSize = describe.getFileSize();
            int count = 0;
            long size = 0;
            File file = new File(fileName);
            FileOutputStream outputStream = null;
            BufferedInputStream inputStream = null;

            try {
                byte[] bytes = new byte[4096];
                outputStream = new FileOutputStream(file);
                inputStream = new BufferedInputStream(connSocket.getInputStream());
                while ((count = inputStream.read(bytes)) != -1) {
                    System.out.println("count is " + count);
                    outputStream.write(bytes, 0, count);
                    outputStream.flush();
                    size += count;
                    if (size >= fileSize)
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (outputStream != null)
                        outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private static void newVisionSend(@NotNull String data) {
        try {
            byte[] bytes = data.getBytes("UTF-8");
            connSocket.getOutputStream().write(bytes);
            connSocket.getOutputStream().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void send2service() {
        sendMsg(Parameter.CONNECTED_SUCCESS + "_" + Parameter.END_FLAG);
    }
}
