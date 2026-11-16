using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;
using Microsoft.Win32;

class UninstallForm : Form {
    const string APP = "烂头背枪游戏合集";
    const string REG = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\烂头背枪游戏合集";
    const string LNK = "烂头背枪游戏合集.lnk";
    string installDir;

    [STAThread]
    static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new UninstallForm());
    }

    public UninstallForm() {
        installDir = Path.GetDirectoryName(Application.ExecutablePath);
        Text = "卸载 - " + APP;
        ClientSize = new Size(440, 210);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(24, 27, 38);
        ForeColor = Color.FromArgb(228, 231, 240);
        Font = new Font("微软雅黑", 9F);

        Label title = new Label();
        title.Text = "卸载 " + APP;
        title.Font = new Font("微软雅黑", 14F, FontStyle.Bold);
        title.ForeColor = Color.FromArgb(228, 231, 240);
        title.Location = new Point(24, 24);
        title.AutoSize = true;
        Controls.Add(title);

        Label msg = new Label();
        msg.Text = "确认要完全卸载本游戏吗？\n将删除游戏文件、快捷方式及注册表信息。";
        msg.Font = new Font("微软雅黑", 10F);
        msg.ForeColor = Color.FromArgb(160, 166, 186);
        msg.Location = new Point(24, 66);
        msg.Size = new Size(390, 50);
        Controls.Add(msg);

        Button okBtn = new Button();
        okBtn.Text = "确认卸载";
        okBtn.Size = new Size(110, 36);
        okBtn.Location = new Point(110, 150);
        okBtn.FlatStyle = FlatStyle.Flat;
        okBtn.BackColor = Color.FromArgb(170, 60, 60);
        okBtn.ForeColor = Color.White;
        okBtn.Cursor = Cursors.Hand;
        okBtn.Click += DoUninstall;
        Controls.Add(okBtn);

        Button cancelBtn = new Button();
        cancelBtn.Text = "取消";
        cancelBtn.Size = new Size(110, 36);
        cancelBtn.Location = new Point(240, 150);
        cancelBtn.FlatStyle = FlatStyle.Flat;
        cancelBtn.BackColor = Color.FromArgb(42, 46, 62);
        cancelBtn.ForeColor = Color.FromArgb(228, 231, 240);
        cancelBtn.Cursor = Cursors.Hand;
        cancelBtn.Click += delegate { Close(); };
        Controls.Add(cancelBtn);
    }

    void DoUninstall(object sender, EventArgs e) {
        try {
            string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
            string dlnk = Path.Combine(desktop, LNK);
            if (File.Exists(dlnk)) File.Delete(dlnk);
            string smDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.StartMenu),
                "Programs", APP);
            if (Directory.Exists(smDir)) Directory.Delete(smDir, true);
            try { Registry.CurrentUser.DeleteSubKey(REG, false); } catch { }
            Hide();
            ProcessStartInfo psi = new ProcessStartInfo("cmd.exe",
                "/c ping 127.0.0.1 -n 3 > nul & rd /s /q \"" + installDir + "\"");
            psi.CreateNoWindow = true;
            psi.WindowStyle = ProcessWindowStyle.Hidden;
            psi.WorkingDirectory = Environment.GetEnvironmentVariable("SystemRoot");
            Process.Start(psi);
            MessageBox.Show("卸载完成！", "卸载",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            Application.Exit();
        } catch (Exception ex) {
            MessageBox.Show("卸载出错：" + ex.Message, "错误",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}