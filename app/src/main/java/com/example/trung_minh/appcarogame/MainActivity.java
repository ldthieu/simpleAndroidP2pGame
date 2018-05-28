package com.example.trung_minh.appcarogame;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static android.net.wifi.p2p.WifiP2pManager.*;

public class MainActivity extends AppCompatActivity {

    Button btnOnOff, btnDiscover, btnSend, btnCaro;
    ListView listView;
    TextView read_msg_box, connectionStatus;
    EditText writeMsg;

    RelativeLayout layoutMain, layoutCaro;

    WifiManager wifiManager;
    WifiP2pManager mManager;
    Channel mChanel;

    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;

    final static int CONNECT_OK = 1;
    final static int READ_MESSAGE = 2;
    final static int INVITE = 3;
    final static int ACCEPT = 4;
    final static int MOVE = 5;

    ServerClass serverClass;
    ClientClass clientClass;
    SendReceive sendReceive;

    // game
    final static int Maxsize = 20;
    private Context context;
    private Drawable[] drawCell = new Drawable[4];//0: default(empty) 1:player 2:bot 3:backgroud
    private ImageView[][] ivCell = new ImageView[Maxsize][Maxsize];
    private int[][] valueCell = new int[Maxsize][Maxsize];//0: empty 1:is x 2: is o
    private int winner_play;
    private int gameState = -1;
    private Button btnPlay;
    private TextView tvTurn;

    final static int STATE_STANDBY = 0;
    final static int STATE_MYTURN = 1;
    final static int STATE_OPPONENTTURN = 2;

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECT_OK:
                    Toast.makeText(getApplicationContext(), "Ready to play!", Toast.LENGTH_SHORT).show();
                    enableItem(true);
                    break;
                case READ_MESSAGE:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    read_msg_box.setText(tempMsg);
                    break;
                case INVITE:
                    Toast.makeText(getApplicationContext(), "Invited!", Toast.LENGTH_SHORT).show();
                    sendReceive.write(ACCEPT);
                    changeState(STATE_OPPONENTTURN);
                    initGame();
                    layoutMain.setVisibility(View.GONE);
                    layoutCaro.setVisibility(View.VISIBLE);
                    break;
                case ACCEPT:
                    Toast.makeText(getApplicationContext(), "The opponent has agreed!", Toast.LENGTH_SHORT).show();
                    changeState(STATE_MYTURN);
                    initGame();
                    layoutMain.setVisibility(View.GONE);
                    layoutCaro.setVisibility(View.VISIBLE);
                    break;
                case MOVE:
                    Toast.makeText(getApplicationContext(), "The opponent moved in x: " + msg.arg1 + ", y: " + msg.arg2, Toast.LENGTH_SHORT).show();
                    make_a_move(msg.arg1, msg.arg2, STATE_OPPONENTTURN);
            }
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        initialWork();
        exqListener();
    }

    private void exqListener() {
        btnOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(false);
                    btnOnOff.setText("ON");
                } else {
                    wifiManager.setWifiEnabled(true);
                    btnOnOff.setText("OFF");
                }
            }
        });

        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mManager.discoverPeers(mChanel, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Discovery Started");
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.w("connectError", i + "");
                        connectionStatus.setText("Discovery Starting Failed");
                    }
                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;

                mManager.connect(mChanel, config, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getApplicationContext(), "Connect to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.w("connectPeerError", "reason" + i);
                        Toast.makeText(getApplicationContext(), "Not connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sendReceive != null) {
                    String msg = writeMsg.getText().toString();
                    sendReceive.write(READ_MESSAGE, msg.getBytes());
                } else {
                    Toast.makeText(getApplicationContext(), "Connection error!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnCaro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendReceive != null) {
                    sendReceive.write(INVITE);
                } else {
                    Toast.makeText(getApplicationContext(), "Connection error!", Toast.LENGTH_SHORT).show();
                }
//                layoutMain.setVisibility(View.GONE);
//                layoutCaro.setVisibility(View.VISIBLE);
//                initGame();
//                make_a_move(7,7,1);
//                make_a_move(11,4,2);
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    private void initialWork() {
        layoutMain = findViewById(R.id.layoutMain);
        layoutCaro = findViewById(R.id.layoutCaro);
        btnOnOff = (Button) findViewById(R.id.onOff);
        btnDiscover = (Button) findViewById(R.id.discover);
        btnSend = (Button) findViewById(R.id.sendButton);
        listView = (ListView) findViewById(R.id.peerListView);
        read_msg_box = (TextView) findViewById(R.id.readMsg);
        connectionStatus = (TextView) findViewById(R.id.connectionStatus);
        writeMsg = (EditText) findViewById(R.id.writeMsg);

        btnPlay = (Button) findViewById(R.id.btnPlay);
        tvTurn = (TextView) findViewById(R.id.tvTurn);

        btnCaro = findViewById(R.id.btnCaro);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChanel = mManager.initialize(this, getMainLooper(), null);

        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChanel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        enableItem(false);
    }

    PeerListListener peerListListener = new PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            Log.w("checkPeers", "Found some peers!!! " + peerList.getDeviceList().size());
            if (!peerList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];

                int index = 0;
                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(adapter);
            }

            if (peers.size() == 0) {
                Toast.makeText(getApplicationContext(), "No Device Found", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };

    ConnectionInfoListener connectionInfoListener = new ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;

            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionStatus.setText("Host");
                serverClass = new ServerClass();
                serverClass.start();
            } else if (wifiP2pInfo.groupFormed) {
                connectionStatus.setText("Client");
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }
        }
    };

    private void enableItem(boolean value){
        btnCaro.setEnabled(value);
        btnSend.setEnabled(value);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectP2p();

        if (sendReceive != null) {
            sendReceive.closeConnect();
        }
        if (serverClass != null) {
            serverClass.closeServerSocket();
        }
    }

    private void disconnectP2p(){
        if(mManager != null && mChanel != null){
            mManager.requestGroupInfo(mChanel, new GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && mManager != null && mChanel != null
                            && group.isGroupOwner()) {
                        mManager.removeGroup(mChanel, new ActionListener() {

                            @Override
                            public void onSuccess() {
                                Log.d("Disconnect", "removeGroup onSuccess -");
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d("Disconnect", "removeGroup onFailure -" + reason);
                            }
                        });
                    }
                }
            });
        }
    }

    private class SendReceive extends Thread {
        private Socket socket;
        private DataInputStream dataInputStream;
        private DataOutputStream dataOutputStream;

        public SendReceive(Socket skt) {
            socket = skt;
            try {
                dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                handler.obtainMessage(CONNECT_OK).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void closeConnect() {
            if (socket != null) {
                if (socket.isConnected()) {
                    try {
                        dataOutputStream.close();
                        dataInputStream.close();
                        socket.close();
                    } catch (IOException e) {
                        Log.e("closeConnect", "socket close error!");
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int what, arg1, arg2, bytes;
            while (socket != null) {
                try {
                    what = dataInputStream.readInt();
                    switch (what) {
                        case READ_MESSAGE:
                            bytes = dataInputStream.read(buffer);
                            if (bytes > 0) {
                                handler.obtainMessage(READ_MESSAGE, bytes, -1, buffer).sendToTarget();
                            }
                            break;
                        case INVITE:
                        case ACCEPT:
                        case CONNECT_OK:
                            handler.obtainMessage(what).sendToTarget();
                            break;
                        case MOVE:
                            arg1 = dataInputStream.readInt();
                            arg2 = dataInputStream.readInt();
                            if (arg1 != -1 && arg2 != -1) {
                                handler.obtainMessage(what, arg1, arg2).sendToTarget();
                            }
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(int what, byte[] bytes) {
            try {
                dataOutputStream.writeInt(what);
                dataOutputStream.write(bytes);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void write(int what) {
            try {
                dataOutputStream.writeInt(what);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void write(int what, int agr1, int agr2) {
            try {
                dataOutputStream.writeInt(what);
                dataOutputStream.writeInt(agr1);
                dataOutputStream.writeInt(agr2);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ServerClass extends Thread {
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                //serverSocket.setReuseAddress(true);
                serverSocket = new ServerSocket(5656);
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void closeServerSocket() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ClientClass extends Thread {
        Socket socket;
        String hostAdd;

        public ClientClass(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 5656), 500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Game

    private float screenWidth() {
        Resources resources = context.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        return dm.heightPixels;
    }

    private void changeState(int state){
        if(state == STATE_MYTURN){
            gameState = STATE_MYTURN;
            tvTurn.setText("You");
        }
        if(state == STATE_OPPONENTTURN){
            gameState = STATE_OPPONENTTURN;
            tvTurn.setText("Your Opponent");
        }
        if(state == STATE_STANDBY){
            gameState = STATE_STANDBY;
            tvTurn.setText("Click button New game to play new game!");
        }
    }

    private void make_a_move(int x, int y, int turn) {
        ivCell[x][y].setImageDrawable(drawCell[turn]);
        valueCell[x][y] = turn;

        //check if anyone win
        if (checkWinner(x,y)) {
            changeState(STATE_STANDBY);
            return;
        } else {
            if (gameState == STATE_MYTURN) {
                changeState(STATE_OPPONENTTURN);
            } else if (gameState == STATE_OPPONENTTURN) {
                changeState(STATE_MYTURN);
            }
        }
    }

    private String horizontal(int x) {
        String st = "";
        for (int i = 0; i < Maxsize; i++) {
            st = st + valueCell[x][i];
        }
        Log.w("HORIZONTAL", st);
        return st;
    }

    private String vertical(int y) {
        String st = "";
        for (int i = 0; i < Maxsize; i++) {
            st = st + valueCell[i][y];
        }
        Log.w("vertical", st);
        return st;
    }

    private String mainDiagonal(int xMove, int yMove) {
        String st = "";
        int x = xMove, y = yMove;
        while (true) {
            if (inBroad(x, y)) {
                x--;
                y--;
            } else {
                break;
            }
        }
        Log.w("main Diagonal", x + ":" + y);
        while (true) {
            x++;
            y++;
            if (inBroad(x, y)) {
                Log.w("main Diagonal", x + ":" + y);
                st = st + valueCell[x][y];
            } else {
                break;
            }
        }
        Log.w("main Diagonal", st);
        return st;
    }

    private String subDiagonal(int xMove, int yMove) {
        String st = "";
        int x = xMove, y = yMove;
        while (true) {
            if (inBroad(x, y)) {
                x--;
                y++;
            } else {
                break;
            }
        }
        Log.w("sub Diagonal", x + ":" + y);
        while (true) {
            x++;
            y--;
            if (inBroad(x, y)) {
                Log.w("sub Diagonal", x + ":" + y);
                st = st + valueCell[x][y];
            } else {
                break;
            }
        }
        Log.w("main Diagonal", st);
        return st;
    }

    private boolean inBroad(int i, int j) {
        if (i < 0 || i > Maxsize - 1 || j < 0 || j > Maxsize - 1) return false;
        return true;
    }

    private boolean checkWinner(int x, int y) {
        if (winner_play != 0) return true;
        //check in row
        String stCheck = horizontal(x) + "." + vertical(y) + "." + mainDiagonal(x,y) + "." + subDiagonal(x,y);
        Log.w("sum", stCheck);
        if (stCheck.contains("11111") || stCheck.contains("011110")) {
            winner_play = 1;
            Toast.makeText(context, "You Win", Toast.LENGTH_LONG).show();
            tvTurn.setText("You Win");
            return true;
        } else if (stCheck.contains("22222") || stCheck.contains("022220")) {
            winner_play = 2;
            Toast.makeText(context, "You Lost", Toast.LENGTH_LONG).show();
            tvTurn.setText("You Lost");
            return true;
        }
        return false;
    }

    private void initGame() {
        if (gameState == -1) {
            changeState(STATE_STANDBY);
        }

        loadResources();
        designBoardGame();

        for(int i= 0; i < Maxsize; i++){
            for (int j = 0 ; j < Maxsize;j++){
                ivCell[i][j].setImageDrawable(drawCell[0]);//default or Empty  cell
                valueCell[i][j] = 0;
            }
        }
    }

    private void loadResources() {
        drawCell[0] = null;
        drawCell[1] = context.getResources().getDrawable(R.drawable.x_1);
        drawCell[2] = context.getResources().getDrawable(R.drawable.o_1);
        drawCell[3] = context.getResources().getDrawable(R.drawable.cell_bg);
    }

    private void designBoardGame() {
        //create layourparam to optimize size of cell
        //we have a horizatal linerlayout for row
        //which containers maxSize imageView in

        int sizeCell = Math.round(screenWidth() / Maxsize);
        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(sizeCell * Maxsize, sizeCell);
        LinearLayout.LayoutParams lpCell = new LinearLayout.LayoutParams(sizeCell, sizeCell);

        LinearLayout lineBoardGame = (LinearLayout) findViewById(R.id.linBoardGame);

        //create cells
        for (int i = 0; i < Maxsize; i++) {
            LinearLayout linRow = new LinearLayout(context);
            //make a row
            for (int j = 0; j < Maxsize; j++) {
                ivCell[i][j] = new ImageView(context);
                //make a cell
                //need to set background default for cell
                //cell has 3 status: default, player, bot
                ivCell[i][j].setBackground(drawCell[3]);
                final int x = i;
                final int y = j;
                ivCell[i][j].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
//                        winner_play = 0;
//                        gameState = STATE_MYTURN;
                        if (valueCell[x][y] == 0 && winner_play == 0) {
                            if (gameState == STATE_MYTURN) {
                                //gửi tin đi
                                if (sendReceive != null) {
                                    sendReceive.write(MOVE, x, y);
                                    make_a_move(x, y, STATE_MYTURN);
                                } else {
                                    Toast.makeText(getApplicationContext(), "Connection error!", Toast.LENGTH_SHORT).show();
                                }
//                                Toast.makeText(getApplicationContext(), "" + x + " " + y, Toast.LENGTH_SHORT).show();
//                                make_a_move(x,y,2);
                            }
                        }
                    }
                });
                linRow.addView(ivCell[i][j], lpCell);
            }
            lineBoardGame.addView(linRow, lpRow);
        }
    }
}
