using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

class WizardForm : Form {
    const string APP = "烂头背枪游戏合集";
    const string REG = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\烂头背枪游戏合集";
    const string LNK = "烂头背枪游戏合集.lnk";

    Panel content;
    Button prevBtn, nextBtn, cancelBtn;
    int page = 0;
    TextBox dirBox;
    CheckBox deskChk, menuChk, runChk;
    ProgressBar bar;
    Label statusLb;
    string installDir;

    public WizardForm() {
        Text = APP + " 安装向导";
        ClientSize = new Size(560, 400);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(24, 27, 38);
        ForeColor = Color.FromArgb(228, 231, 240);
        Font = new Font("微软雅黑", 9F);

        content = new Panel();
        content.Dock = DockStyle.Fill;
        content.Padding = new Padding(24, 14, 24, 6);
        Controls.Add(content);

        Panel btnBar = new Panel();
        btnBar.Dock = DockStyle.Bottom;
        btnBar.Height = 54;
        btnBar.BackColor = Color.FromArgb(18, 20, 30);
        prevBtn = MkBtn("上一步", 272, 10);
        nextBtn = MkBtn("下一步", 372, 10);
        cancelBtn = MkBtn("取消", 472, 10);
        cancelBtn.Click += delegate { Close(); };
        prevBtn.Click += delegate { if (page == 1) ShowPage(0); };
        nextBtn.Click += OnNext;
        btnBar.Controls.Add(prevBtn);
        btnBar.Controls.Add(nextBtn);
        btnBar.Controls.Add(cancelBtn);
        Controls.Add(btnBar);

        Panel banner = new Panel();
        banner.Dock = DockStyle.Top;
        banner.Height = 72;
        banner.BackColor = Color.FromArgb(18, 20, 30);
        try {
            PictureBox icon = new PictureBox();
            icon.Image = Icon.ExtractAssociatedIcon(Application.ExecutablePath).ToBitmap();
            icon.SizeMode = PictureBoxSizeMode.Zoom;
            icon.SetBounds(20, 16, 40, 40);
            banner.Controls.Add(icon);
        } catch { }
        Label title = new Label();
        title.Text = APP;
        title.Font = new Font("微软雅黑", 15F, FontStyle.Bold);
        title.ForeColor = Color.FromArgb(212, 175, 55);
        title.AutoSize = true;
        title.Location = new Point(72, 14);
        banner.Controls.Add(title);
        Label sub = new Label();
        sub.Text = "安装向导  ·  六合一经典游戏合集";
        sub.Font = new Font("微软雅黑", 9F);
        sub.ForeColor = Color.FromArgb(120, 126, 146);
        sub.AutoSize = true;
        sub.Location = new Point(74, 44);
        banner.Controls.Add(sub);
        Panel line = new Panel();
        line.Dock = DockStyle.Bottom;
        line.Height = 2;
        line.BackColor = Color.FromArgb(212, 175, 55);
        banner.Controls.Add(line);
        Controls.Add(banner);

        ShowPage(0);
    }

    Button MkBtn(string text, int x, int y) {
        Button b = new Button();
        b.Text = text;
        b.Size = new Size(88, 34);
        b.Location = new Point(x, y);
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderColor = Color.FromArgb(60, 66, 86);
        b.BackColor = Color.FromArgb(42, 46, 62);
        b.ForeColor = Color.FromArgb(228, 231, 240);
        b.Font = new Font("微软雅黑", 9F);
        b.Cursor = Cursors.Hand;
        return b;
    }

    Label MkLabel(string text, float size, FontStyle style, Color color) {
        Label l = new Label();
        l.Text = text;
        l.Font = new Font("微软雅黑", size, style);
        l.ForeColor = color;
        l.AutoSize = true;
        return l;
    }

    CheckBox MkCheck(string text, int x, int y, bool check) {
        CheckBox c = new CheckBox();
        c.Text = text;
        c.Checked = check;
        c.Font = new Font("微软雅黑", 10F);
        c.ForeColor = Color.FromArgb(228, 231, 240);
        c.Location = new Point(x, y);
        c.AutoSize = true;
        c.Cursor = Cursors.Hand;
        return c;
    }

    void ShowPage(int p) {
        page = p;
        content.Controls.Clear();
        prevBtn.Visible = (p == 1);
        cancelBtn.Visible = (p == 0 || p == 1);
        nextBtn.Visible = (p == 0 || p == 1 || p == 3);
        nextBtn.Text = (p == 1 ? "安  装" : p == 3 ? "完  成" : "下一步");
        if (p == 0) BuildWelcome();
        else if (p == 1) BuildOptions();
        else if (p == 2) BuildProgress();
        else BuildFinish();
    }

    void BuildWelcome() {
        Label h = MkLabel("欢迎使用 " + APP + " 安装向导", 15F, FontStyle.Bold,
            Color.FromArgb(228, 231, 240));
        h.Location = new Point(0, 8);
        content.Controls.Add(h);
        Label body = MkLabel(
            "本向导将引导你完成安装。\n\n" +
            "游戏合集包含 6 款游戏：\n" +
            "  · 烂头背枪    · 五子棋    · 俄罗斯方块\n" +
            "  · 你画我猜    · 小鸟飞翔  · 欢乐斗地主\n\n" +
            "支持单人游戏与局域网联机对战，\n" +
            "斗地主联机支持人机玩家补位。\n\n" +
            "点击「下一步」继续。",
            10F, FontStyle.Regular, Color.FromArgb(160, 166, 186));
        body.Location = new Point(0, 52);
        body.Size = new Size(490, 240);
        content.Controls.Add(body);
    }

    void BuildOptions() {
        Label h = MkLabel("安装选项", 15F, FontStyle.Bold, Color.FromArgb(228, 231, 240));
        h.Location = new Point(0, 8);
        content.Controls.Add(h);

        Label dirLb = MkLabel("安装目录：", 10F, FontStyle.Regular, Color.FromArgb(160, 166, 186));
        dirLb.Location = new Point(0, 54);
        content.Controls.Add(dirLb);

        dirBox = new TextBox();
        dirBox.Text = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs", APP);
        dirBox.Font = new Font("微软雅黑", 9F);
        dirBox.ForeColor = Color.FromArgb(228, 231, 240);
        dirBox.BackColor = Color.FromArgb(34, 38, 52);
        dirBox.BorderStyle = BorderStyle.FixedSingle;
        dirBox.SetBounds(0, 78, 420, 30);
        content.Controls.Add(dirBox);

        Button browse = MkBtn("浏览...", 430, 76);
        browse.Size = new Size(80, 34);
        browse.Click += delegate {
            FolderBrowserDialog dlg = new FolderBrowserDialog();
            dlg.Description = "选择安装目录";
            if (Directory.Exists(dirBox.Text)) dlg.SelectedPath = dirBox.Text;
            if (dlg.ShowDialog(this) == DialogResult.OK) dirBox.Text = dlg.SelectedPath;
        };
        content.Controls.Add(browse);

        deskChk = MkCheck("创建桌面快捷方式", 0, 130, true);
        content.Controls.Add(deskChk);
        menuChk = MkCheck("创建开始菜单快捷方式", 0, 164, true);
        content.Controls.Add(menuChk);

        Label note = MkLabel("所需磁盘空间：约 230 MB", 9F, FontStyle.Regular,
            Color.FromArgb(110, 116, 136));
        note.Location = new Point(0, 236);
        content.Controls.Add(note);
    }

    void BuildProgress() {
        Label h = MkLabel("正在安装", 15F, FontStyle.Bold, Color.FromArgb(228, 231, 240));
        h.Location = new Point(0, 8);
        content.Controls.Add(h);
        bar = new ProgressBar();
        bar.SetBounds(0, 58, 490, 24);
        bar.ForeColor = Color.FromArgb(212, 175, 55);
        content.Controls.Add(bar);
        statusLb = MkLabel("准备中...", 9F, FontStyle.Regular, Color.FromArgb(120, 126, 146));
        statusLb.Location = new Point(0, 94);
        statusLb.Size = new Size(490, 30);
        content.Controls.Add(statusLb);
    }

    void BuildFinish() {
        Label h = MkLabel("安装完成！", 15F, FontStyle.Bold, Color.FromArgb(110, 220, 130));
        h.Location = new Point(0, 8);
        content.Controls.Add(h);
        Label body = MkLabel(APP + " 已成功安装到：\n" + installDir +
            "\n\n可通过桌面快捷方式或开始菜单启动游戏。",
            10F, FontStyle.Regular, Color.FromArgb(160, 166, 186));
        body.Location = new Point(0, 52);
        body.Size = new Size(490, 100);
        content.Controls.Add(body);
        runChk = MkCheck("立即运行游戏", 0, 170, true);
        content.Controls.Add(runChk);
    }

    void OnNext(object sender, EventArgs e) {
        if (page == 0) { ShowPage(1); return; }
        if (page == 1) { StartInstall(); return; }
        if (page == 3) {
            if (runChk != null && runChk.Checked) LaunchGame();
            Application.Exit();
        }
    }

    void StartInstall() {
        string dir = dirBox.Text.Trim();
        if (dir.Length == 0) {
            MessageBox.Show("请选择安装目录。", "提示");
            return;
        }
        if (File.Exists(Path.Combine(dir, "game.jar"))) {
            DialogResult r = MessageBox.Show("该目录已有游戏，是否覆盖安装？",
                "提示", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (r != DialogResult.Yes) return;
        }
        installDir = dir;
        bool wantDesk = deskChk.Checked;
        bool wantMenu = menuChk.Checked;
        byte[] payload = LoadPayload();
        if (payload == null) {
            MessageBox.Show("安装包数据损坏，请重新获取。", "错误",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return;
        }
        ShowPage(2);
        prevBtn.Enabled = false;
        nextBtn.Enabled = false;
        cancelBtn.Enabled = false;
        new Thread((ThreadStart)delegate {
            try {
                Directory.CreateDirectory(installDir);
                ExtractAll(payload);
                if (wantDesk) CreateDesktopShortcut();
                if (wantMenu) CreateStartMenuShortcut();
                WriteRegistry();
                Invoke((MethodInvoker)delegate {
                    prevBtn.Enabled = true;
                    nextBtn.Enabled = true;
                    ShowPage(3);
                });
            } catch (Exception ex) {
                string msg = ex.Message;
                Invoke((MethodInvoker)delegate {
                    MessageBox.Show("安装失败：" + msg, "错误",
                        MessageBoxButtons.OK, MessageBoxIcon.Error);
                    Close();
                });
            }
        }).Start();
    }

    byte[] LoadPayload() {
        try {
            byte[] all = File.ReadAllBytes(Application.ExecutablePath);
            if (all.Length < 16) return null;
            long zipSize = BitConverter.ToInt64(all, all.Length - 8);
            long zipOffset = all.Length - 8 - zipSize;
            if (zipSize <= 0 || zipOffset < 0) return null;
            if (zipOffset + zipSize + 8 != all.Length) return null;
            if (all[zipOffset] != 0x50 || all[zipOffset + 1] != 0x4B) return null;
            byte[] data = new byte[zipSize];
            Array.Copy(all, zipOffset, data, 0, (int)zipSize);
            return data;
        } catch { return null; }
    }

    void ExtractAll(byte[] zipData) {
        using (MemoryStream ms = new MemoryStream(zipData))
        using (ZipArchive zip = new ZipArchive(ms, ZipArchiveMode.Read)) {
            long total = 0;
            foreach (ZipArchiveEntry e in zip.Entries) total += e.Length;
            long done = 0;
            foreach (ZipArchiveEntry entry in zip.Entries) {
                string rel = entry.FullName.Replace('/', '\\');
                if (rel.Length == 0) continue;
                string dest = Path.Combine(installDir, rel);
                if (rel.EndsWith("\\")) { Directory.CreateDirectory(dest); continue; }
                string d = Path.GetDirectoryName(dest);
                if (d.Length > 0) Directory.CreateDirectory(d);
                using (Stream es = entry.Open())
                using (FileStream fs = File.Create(dest)) es.CopyTo(fs);
                done += entry.Length;
                SetProgress((int)(done * 100 / total), entry.FullName);
            }
        }
        SetProgress(100, "完成");
    }

    void SetProgress(int pct, string file) {
        if (IsDisposed) return;
        if (InvokeRequired) {
            try { Invoke((MethodInvoker)delegate { SetProgress(pct, file); }); } catch { }
            return;
        }
        if (bar != null) bar.Value = Math.Min(pct, 100);
        if (statusLb != null) statusLb.Text = "正在安装: " + file;
    }

    void CreateShortcut(string lnkPath, string target, string args, string workDir, string desc) {
        Type st = Type.GetTypeFromProgID("WScript.Shell");
        object shell = Activator.CreateInstance(st);
        object sc = st.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod,
            null, shell, new object[] { lnkPath });
        Type t = sc.GetType();
        t.InvokeMember("TargetPath", BindingFlags.SetProperty, null, sc, new object[] { target });
        t.InvokeMember("Arguments", BindingFlags.SetProperty, null, sc, new object[] { args });
        t.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, sc, new object[] { workDir });
        t.InvokeMember("IconLocation", BindingFlags.SetProperty, null, sc,
            new object[] { Path.Combine(workDir, "app.ico") + ",0" });
        t.InvokeMember("Description", BindingFlags.SetProperty, null, sc, new object[] { desc });
        t.InvokeMember("Save", BindingFlags.InvokeMethod, null, sc, null);
    }

    void CreateDesktopShortcut() {
        string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
        string javaw = Path.Combine(installDir, "runtime", "bin", "javaw.exe");
        CreateShortcut(Path.Combine(desktop, LNK), javaw, "-cp game.jar Main", installDir, APP);
    }

    void CreateStartMenuShortcut() {
        string programs = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs", APP);
        Directory.CreateDirectory(programs);
        string javaw = Path.Combine(installDir, "runtime", "bin", "javaw.exe");
        CreateShortcut(Path.Combine(programs, LNK), javaw, "-cp game.jar Main", installDir, APP);
        CreateShortcut(Path.Combine(programs, "卸载.lnk"),
            Path.Combine(installDir, "uninstall.exe"), "", installDir, "卸载 " + APP);
    }

    void WriteRegistry() {
        try {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(REG)) {
                key.SetValue("DisplayName", APP);
                key.SetValue("DisplayVersion", "1.0");
                key.SetValue("DisplayIcon", Path.Combine(installDir, "app.ico"));
                key.SetValue("UninstallString", "\"" + Path.Combine(installDir, "uninstall.exe") + "\"");
                key.SetValue("InstallLocation", installDir);
                key.SetValue("Publisher", "烂头背枪");
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
            }
        } catch { }
    }

    void LaunchGame() {
        try {
            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = Path.Combine(installDir, "runtime", "bin", "javaw.exe");
            psi.Arguments = "-cp game.jar Main";
            psi.WorkingDirectory = installDir;
            Process.Start(psi);
        } catch { }
    }

    [STAThread]
    static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new WizardForm());
    }
}