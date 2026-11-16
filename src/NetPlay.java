import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * 联机通信：局域网主机/客户端，行文本协议。
 * ST:<state> 状态同步（主机→客户端/观众：开局布阵/重开/模式/玩家号/昵称/观众数）
 * OP:<cmd>   操作同步（玩家双向，主机转发给其他玩家和观众；观众只收不发）
 * CH:<msg>   聊天消息（玩家和观众双向，主机转发给所有人）
 * 握手：客户端连入后先发 JOIN:PLAYER/JOIN:SPECTATOR，再发 NICK:昵称
 * 支持多客户端主机模式（三人游戏）+ 观众模式。
 */
public class NetPlay {
    public interface NetListener {
        void onConnected();
        void onStateReceived(String state);
        void onOpReceived(String op);
        void onChatReceived(String msg);
        void onDisconnected();
        /** 观众人数变化（主机端回调，用于刷新房间显示） */
        default void onSpectatorChanged(int count) {}
    }

    // 单连接模式（客户端）
    private Socket socket;
    private ServerSocket serverSocket;
    private BufferedReader in;
    private PrintWriter out;

    // 多客户端模式（主机）：玩家客户端
    private List<Socket> multiSockets = new ArrayList<Socket>();
    private List<PrintWriter> multiOuts = new ArrayList<PrintWriter>();
    private List<BufferedReader> multiIns = new ArrayList<BufferedReader>();
    private List<Boolean> multiKicked = new ArrayList<Boolean>();
    private List<String> clientNicks = new ArrayList<String>();
    private List<Integer> clientAvatars = new ArrayList<Integer>();
    private List<Integer> clientGenders = new ArrayList<Integer>();

    // 人机玩家（主机端虚拟，不占网络连接）
    private List<String> botNicks = new ArrayList<String>();
    private List<Integer> botAvatars = new ArrayList<Integer>();
    private List<Integer> botGenders = new ArrayList<Integer>();

    // 观众（主机端）
    private List<Socket> specSockets = new ArrayList<Socket>();
    private List<PrintWriter> specOuts = new ArrayList<PrintWriter>();
    private List<Boolean> specKicked = new ArrayList<Boolean>();
    private List<String> specNicks = new ArrayList<String>();
    private List<Integer> specAvatars = new ArrayList<Integer>();
    private List<Integer> specGenders = new ArrayList<Integer>();

    private volatile boolean accepting = false;
    private int expectedClients = 1;
    private int connectedClients = 0;

    // 自身信息
    private String myNick = "玩家";
    private int myAvatar = 0;                   // 自己的头像索引(0-5)
    private int myGender = 0;                   // 自己的性别(0-4)
    private boolean spectator = false;          // 自己是否观众（客户端）
    private String[] remoteNicks = new String[4]; // 客户端收到的各玩家昵称(1/2/3)
    private int[] remoteAvatars = new int[4];   // 客户端收到的各玩家头像(1/2/3)
    private int[] remoteGenders = new int[4];   // 客户端收到的各玩家性别(1/2/3)
    private int spectatorCount = 0;             // 客户端收到的观众人数

    private NetListener listener;
    private boolean isHost;
    private volatile boolean running;

    /** 切换监听器（主页建连后由游戏窗口接管） */
    public void setListener(NetListener l) { this.listener = l; }

    /** 设置自己的昵称（连接前调用），去除协议分隔符|和, */
    public void setMyNick(String nick) {
        if (nick != null && !nick.trim().isEmpty())
            this.myNick = nick.replace("|", "").replace(",", "").trim();
    }
    public String getMyNick() { return myNick; }

    /** 设置自己的头像/性别（连接前调用） */
    public void setMyAvatar(int a) { this.myAvatar = Math.max(0, Math.min(5, a)); }
    public void setMyGender(int g) { this.myGender = Math.max(0, Math.min(4, g)); }

    /** 自己是否观众 */
    public boolean isSpectator() { return spectator; }

    /** 获取指定玩家的昵称（联机时，1=主机/黑方 2=白方 3=绿方），可能为null */
    public String getPlayerNick(int player) {
        if (isHost) {
            if (player == 1) return myNick;
            if (player == 2) return clientNicks.size() > 0 ? clientNicks.get(0) : null;
            if (player == 3) return clientNicks.size() > 1 ? clientNicks.get(1) : null;
            return null;
        }
        if (player >= 1 && player <= 3) return remoteNicks[player];
        return null;
    }

    /** 获取观众人数（主机动态计算，客户端取缓存值） */
    public int getSpectatorCount() {
        if (isHost) return aliveSpectators();
        return spectatorCount;
    }

    /** 获取指定玩家的头像索引（1=主机 2=白方 3=绿方） */
    public int getPlayerAvatar(int player) {
        if (isHost) {
            if (player == 1) return myAvatar;
            if (player == 2) return clientAvatars.size() > 0 ? clientAvatars.get(0) : 0;
            if (player == 3) return clientAvatars.size() > 1 ? clientAvatars.get(1) : 0;
            return 0;
        }
        if (player >= 1 && player <= 3) return remoteAvatars[player];
        return 0;
    }

    /** 获取指定玩家的性别（1=主机 2=白方 3=绿方） */
    public int getPlayerGender(int player) {
        if (isHost) {
            if (player == 1) return myGender;
            if (player == 2) return clientGenders.size() > 0 ? clientGenders.get(0) : 0;
            if (player == 3) return clientGenders.size() > 1 ? clientGenders.get(1) : 0;
            return 0;
        }
        if (player >= 1 && player <= 3) return remoteGenders[player];
        return 0;
    }

    /** 主机：获取存活观众信息列表，每项为 String[]{昵称, 头像, 性别} */
    public List<String[]> getSpectatorInfos() {
        List<String[]> list = new ArrayList<String[]>();
        for (int i = 0; i < specSockets.size(); i++) {
            Socket s = specSockets.get(i);
            if (s != null && !s.isClosed()) {
                list.add(new String[]{ specNicks.get(i),
                        String.valueOf(specAvatars.get(i)),
                        String.valueOf(specGenders.get(i)) });
            }
        }
        return list;
    }

    /** 主机监听多个客户端（持续接受直到stopAccept；玩家上限clientCount，观众不限） */
    public void hostMulti(int port, int clientCount, NetListener l) throws IOException {
        isHost = true;
        this.listener = l;
        this.expectedClients = clientCount;
        this.connectedClients = 0;
        running = true;
        accepting = true;
        serverSocket = new ServerSocket(port);
        new Thread(new Runnable() {
            public void run() {
                while (accepting) {
                    try {
                        Socket s = serverSocket.accept();
                        handleHandshake(s);
                    } catch (IOException e) {
                        if (accepting) notifyDisconnect();
                        return;
                    }
                }
            }
        }).start();
    }

    /** 主机处理新连接握手：读JOIN/NICK两行，分流玩家/观众 */
    private void handleHandshake(Socket s) {
        try {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String joinLine = br.readLine();
            String nickLine = br.readLine();
            boolean isSpec = joinLine != null && joinLine.startsWith("JOIN:SPECTATOR");
            String nick = "玩家";
            int av = 0, gd = 0;
            if (nickLine != null && nickLine.startsWith("NICK:")) {
                // 扩展格式 NICK:昵称|头像|性别
                String[] nf = nickLine.substring(5).trim().split("\\|", -1);
                if (nf.length > 0 && !nf[0].trim().isEmpty()) nick = nf[0].trim();
                if (nf.length > 1) try { av = Integer.parseInt(nf[1].trim()); } catch (Exception e) { }
                if (nf.length > 2) try { gd = Integer.parseInt(nf[2].trim()); } catch (Exception e) { }
            }
            if (isSpec) {
                // 观众：不限人数
                specSockets.add(s);
                specOuts.add(pw);
                specKicked.add(false);
                specNicks.add(nick);
                specAvatars.add(av);
                specGenders.add(gd);
                final int idx = specSockets.size() - 1;
                startSpectatorReceive(br, idx);
                notifySpectatorChanged();
                notifyConnected();
                broadcastRoomInfo(); // 同步昵称和观众数给所有人
            } else {
                // 玩家：超过上限则拒绝
                if (connectedClients >= expectedClients) {
                    pw.println("ST:FULL");
                    try { Thread.sleep(100); } catch (Exception e) { }
                    try { s.close(); } catch (IOException e2) { }
                    return;
                }
                multiSockets.add(s);
                multiOuts.add(pw);
                multiIns.add(br);
                multiKicked.add(false);
                clientNicks.add(nick);
                clientAvatars.add(av);
                clientGenders.add(gd);
                final int idx = multiSockets.size() - 1;
                startMultiReceive(br, idx);
                connectedClients++;
                notifyConnected();
                broadcastRoomInfo();
            }
        } catch (IOException e) {
            try { s.close(); } catch (IOException e2) { }
        }
    }

    /** 客户端连接（asSpectator=true以观众身份加入） */
    public void connect(String ip, int port, NetListener l, boolean asSpectator) throws IOException {
        isHost = false;
        this.listener = l;
        this.spectator = asSpectator;
        socket = new Socket(ip, port);
        initStreams();
        // 握手：告知身份、昵称、头像、性别
        out.println(asSpectator ? "JOIN:SPECTATOR" : "JOIN:PLAYER");
        out.println("NICK:" + myNick + "|" + myAvatar + "|" + myGender);
        startReceive();
        notifyConnected();
    }

    private void initStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    /** 单连接接收线程（客户端） */
    private void startReceive() {
        running = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        dispatch(line);
                    }
                } catch (IOException e) { }
                notifyDisconnect();
            }
        }).start();
    }

    /** 多客户端接收线程（主机为每个玩家客户端启动），主机负责转发OP/CH给其他玩家和观众 */
    private void startMultiReceive(final BufferedReader br, final int idx) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String line;
                    while (running && (line = br.readLine()) != null) {
                        if (line.startsWith("OP:DDZ_")) {
                            // 斗地主操作仅主机处理，不转发（防作弊）
                        } else if (line.startsWith("OP:") || line.startsWith("CH:")) {
                            for (int i = 0; i < multiOuts.size(); i++) {
                                if (i != idx) multiOuts.get(i).println(line);
                            }
                            for (PrintWriter pw : specOuts) pw.println(line);
                        }
                        dispatch(line);
                    }
                } catch (IOException e) { }
                if (idx < multiKicked.size() && !multiKicked.get(idx)) {
                    notifyDisconnect();
                }
            }
        }).start();
    }

    /** 观众接收线程（主机为每个观众启动），观众只能发聊天 */
    private void startSpectatorReceive(final BufferedReader br, final int idx) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String line;
                    while (running && (line = br.readLine()) != null) {
                        if (line.startsWith("CH:")) {
                            for (PrintWriter pw : multiOuts) pw.println(line);
                            for (int i = 0; i < specOuts.size(); i++) {
                                if (i != idx) specOuts.get(i).println(line);
                            }
                            dispatch(line);
                        }
                    }
                } catch (IOException e) { }
                if (idx < specKicked.size() && !specKicked.get(idx)) {
                    // 观众断开：通知房间刷新
                    notifySpectatorChanged();
                    notifyConnected();
                }
            }
        }).start();
    }

    /** 解析协议消息并回调监听器（EDT线程），截获NICKS/SPEC更新本地缓存 */
    private void dispatch(String msg) {
        if (msg.startsWith("ST:")) {
            final String body = msg.substring(3);
            if (body.startsWith("NICKS:")) {
                parseNicks(body.substring(6));
            } else if (body.startsWith("SPEC:")) {
                try { spectatorCount = Integer.parseInt(body.substring(5).trim()); } catch (Exception e) { }
            }
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { if (listener != null) listener.onStateReceived(body); }
            });
        } else if (msg.startsWith("OP:")) {
            final String body = msg.substring(3);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { if (listener != null) listener.onOpReceived(body); }
            });
        } else if (msg.startsWith("CH:")) {
            final String body = msg.substring(3);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { if (listener != null) listener.onChatReceived(body); }
            });
        }
    }

    /** 解析 NICKS 数据：每槽位格式 昵称,头像,性别，槽位间以|分隔 */
    private void parseNicks(String data) {
        String[] parts = data.split("\\|", -1);
        for (int i = 0; i < parts.length && i < 3; i++) {
            String p = parts[i];
            if (p.isEmpty()) { remoteNicks[i + 1] = null; continue; }
            String[] f = p.split(",", -1);
            remoteNicks[i + 1] = f[0].isEmpty() ? null : f[0];
            if (f.length > 1) try { remoteAvatars[i + 1] = Integer.parseInt(f[1].trim()); } catch (Exception e) { }
            if (f.length > 2) try { remoteGenders[i + 1] = Integer.parseInt(f[2].trim()); } catch (Exception e) { }
        }
    }

    public void sendState(String state) { send("ST:" + state); }
    public void sendOp(String op) { send("OP:" + op); }
    public void sendChat(String msg) { send("CH:" + msg); }

    /** 发送：多客户端主机模式广播给所有玩家客户端和观众，其余发给单连接 */
    private synchronized void send(String msg) {
        if (isHost && !multiOuts.isEmpty()) {
            for (PrintWriter pw : multiOuts) pw.println(msg);
            for (PrintWriter pw : specOuts) pw.println(msg);
        } else if (out != null) {
            out.println(msg);
        }
    }

    /** 多客户端模式下发送给指定玩家客户端（idx从0开始），用于分配玩家号 */
    public synchronized void sendToClient(int idx, String msg) {
        if (isHost && idx >= 0 && idx < multiOuts.size()) {
            multiOuts.get(idx).println(msg);
        }
    }

    /** 主机：添加一个人机玩家（昵称"电脑N"，性别4=非人），返回人机序号（0起）；满2个返回-1 */
    public synchronized int addBot() {
        if (!isHost || botNicks.size() >= 2) return -1;
        int n = botNicks.size() + 1;
        botNicks.add("电脑" + n);
        botAvatars.add(0);
        botGenders.add(4);   // 非人
        broadcastRoomInfo();
        return botNicks.size() - 1;
    }

    /** 主机：移除最后一个人机玩家 */
    public synchronized void removeBot() {
        if (!isHost || botNicks.isEmpty()) return;
        int last = botNicks.size() - 1;
        botNicks.remove(last);
        botAvatars.remove(last);
        botGenders.remove(last);
        broadcastRoomInfo();
    }

    /** 主机：获取人机数量 */
    public int getBotCount() { return botNicks.size(); }

    /** 主机：获取指定人机的昵称（idx从0起） */
    public String getBotNick(int idx) {
        return idx >= 0 && idx < botNicks.size() ? botNicks.get(idx) : null;
    }
    public int getBotAvatar(int idx) {
        return idx >= 0 && idx < botAvatars.size() ? botAvatars.get(idx) : 0;
    }
    public int getBotGender(int idx) {
        return idx >= 0 && idx < botGenders.size() ? botGenders.get(idx) : 0;
    }

    /** 主机：判断玩家槽位（2或3）是否由人机占据（与 broadcastRoomInfo 填充逻辑一致） */
    public synchronized boolean isSeatBot(int seat) {
        if (!isHost) return false;
        int connected = clientNicks.size();
        int bots = botNicks.size();
        int botUsed = 0;
        // 槽位2
        boolean s2bot = false;
        if (connected == 0 && botUsed < bots) { s2bot = true; botUsed++; }
        // 槽位3
        boolean s3bot = false;
        if (connected < 2 && botUsed < bots) { s3bot = true; botUsed++; }
        if (seat == 2) return s2bot;
        if (seat == 3) return s3bot;
        return false;
    }

    /** 主机：广播房间信息（玩家昵称/头像/性别+观众数）给所有玩家和观众。
     *  格式 ST:NICKS:昵称,头像,性别|昵称,头像,性别|昵称,头像,性别（3个玩家槽位）
     *  槽位2/3 优先填真人客户端，空位用人机填充 */
    public synchronized void broadcastRoomInfo() {
        if (!isHost) return;
        String p1 = myNick + "," + myAvatar + "," + myGender;
        // 人机按顺序填充未被真人客户端占据的槽位
        int botUsed = 0;
        // 槽位2：真人客户端0 或 人机
        String p2;
        if (clientNicks.size() > 0) {
            p2 = clientNicks.get(0) + "," + clientAvatars.get(0) + "," + clientGenders.get(0);
        } else if (botUsed < botNicks.size()) {
            p2 = botNicks.get(botUsed) + "," + botAvatars.get(botUsed) + "," + botGenders.get(botUsed);
            botUsed++;
        } else {
            p2 = "";
        }
        // 槽位3：真人客户端1 或 人机
        String p3;
        if (clientNicks.size() > 1) {
            p3 = clientNicks.get(1) + "," + clientAvatars.get(1) + "," + clientGenders.get(1);
        } else if (botUsed < botNicks.size()) {
            p3 = botNicks.get(botUsed) + "," + botAvatars.get(botUsed) + "," + botGenders.get(botUsed);
            botUsed++;
        } else {
            p3 = "";
        }
        String msg = "ST:NICKS:" + p1 + "|" + p2 + "|" + p3;
        for (PrintWriter pw : multiOuts) pw.println(msg);
        for (PrintWriter pw : specOuts) pw.println(msg);
        String specMsg = "ST:SPEC:" + aliveSpectators();
        for (PrintWriter pw : multiOuts) pw.println(specMsg);
        for (PrintWriter pw : specOuts) pw.println(specMsg);
    }

    /** 获取已连接的玩家客户端数量（多客户端主机模式） */
    public int getConnectedClients() { return connectedClients; }

    private int aliveSpectators() {
        int n = 0;
        for (int i = 0; i < specSockets.size(); i++) {
            Socket s = specSockets.get(i);
            if (s != null && !s.isClosed()) n++;
        }
        return n;
    }

    private void notifyConnected() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (listener != null) listener.onConnected();
            }
        });
    }

    private void notifyDisconnect() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (listener != null) listener.onDisconnected();
            }
        });
    }

    private void notifySpectatorChanged() {
        final int count = aliveSpectators();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (listener != null) listener.onSpectatorChanged(count);
            }
        });
    }

    public boolean isHost() { return isHost; }

    public boolean isConnected() {
        if (isHost && !multiSockets.isEmpty()) {
            return connectedClients >= 1;
        }
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** 关闭监听端口（不再接受新连接，已有连接保持） */
    public void stopAccept() {
        accepting = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) { }
    }

    /** 断开指定玩家客户端（多客户端主机模式），被踢客户端不触发断开通知 */
    public synchronized void kickClient(int idx) {
        if (isHost && idx >= 0 && idx < multiSockets.size()) {
            multiKicked.set(idx, true);
            try { multiSockets.get(idx).close(); } catch (IOException e) { }
        }
    }

    public void close() {
        running = false;
        accepting = false;
        try { if (socket != null) socket.close(); } catch (IOException e) { }
        for (Socket s : multiSockets) {
            try { s.close(); } catch (IOException e) { }
        }
        for (Socket s : specSockets) {
            try { s.close(); } catch (IOException e) { }
        }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) { }
    }
}