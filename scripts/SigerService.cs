// Host de servicio Windows de SIGER (sin binarios de terceros). Install-Service.ps1 lo compila con el compilador de
// .NET Framework incluido en Windows (Add-Type) y lo registra bajo la cuenta propietaria de DPAPI.
// - Inicio: ejecuta scripts\Service.ps1 de la instancia (comprobaciones, secretos DPAPI solo en el proceso, Java 25).
//   El arbol de procesos queda en un Job Object con cierre al terminar: si el host cae, no quedan procesos huerfanos.
// - Detencion: crea run\stop.request; SIGER cierra su contexto (termina ciclos en curso y libera leases) y sale.
//   Si no termina en el plazo, se termina el Job.
// - Caida inesperada del proceso hijo: el host termina con error para que el administrador de servicios aplique la
//   politica de recuperacion (reinicio con espera) configurada por Install-Service.ps1.
// Compatible con C# 5 (Windows PowerShell 5.1).
using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.ServiceProcess;

public sealed class SigerService : ServiceBase {
    private const int DefaultStopWaitMs = 180000;
    private readonly string root;
    private readonly int stopWaitMs;
    private readonly bool probing;
    private Process child;
    private IntPtr job = IntPtr.Zero;
    private volatile bool stopping;

    public SigerService(string root) : this(root, DefaultStopWaitMs, false) { }
    private SigerService(string root, int stopWaitMs, bool probing) {
        this.root = root;
        this.stopWaitMs = stopWaitMs;
        this.probing = probing;
        ServiceName = "SIGER";
        CanStop = true;
        CanShutdown = true;
        AutoLog = true;
    }

    public static int Main(string[] args) {
        // Modo de prueba en consola (sin administrador de servicios): misma supervision, para verificar la parada
        // ordenada, el plazo y la deteccion de caidas. SigerService.exe --probar <raiz> <segundos> [plazo_ms]
        if (args.Length >= 3 && args[0] == "--probar") return Probe(args);
        if (args.Length != 1 || !Path.IsPathRooted(args[0]) || !File.Exists(Path.Combine(args[0], "config\\instance.json"))) {
            Console.Error.WriteLine("Uso: SigerService.exe <raiz de la instancia>");
            return 2;
        }
        ServiceBase.Run(new SigerService(Path.GetFullPath(args[0]).TrimEnd('\\')));
        return 0;
    }

    private static int Probe(string[] args) {
        string root = Path.GetFullPath(args[1]).TrimEnd('\\');
        int seconds = int.Parse(args[2]);
        int wait = args.Length > 3 ? int.Parse(args[3]) : DefaultStopWaitMs;
        var host = new SigerService(root, wait, true);
        var clock = Stopwatch.StartNew();
        host.OnStart(new string[0]);
        bool early = host.child.WaitForExit(seconds * 1000);
        if (early) {
            Console.WriteLine("RESULTADO=CAIDA codigo=" + host.child.ExitCode + " ms=" + clock.ElapsedMilliseconds + " (el servicio terminaria con error y se reiniciaria)");
            Native.CloseHandle(host.job);
            return 1;
        }
        var stop = Stopwatch.StartNew();
        bool forced = host.StopChild();
        Console.WriteLine("RESULTADO=" + (forced ? "TERMINADO_POR_PLAZO" : "PARADA_ORDENADA") + " codigo=" + host.child.ExitCode + " ms=" + stop.ElapsedMilliseconds);
        return 0;
    }

    protected override void OnStart(string[] args) {
        string stop = StopFile();
        if (File.Exists(stop)) File.Delete(stop);
        job = Native.CreateJobObject(IntPtr.Zero, null);
        if (job == IntPtr.Zero) throw new InvalidOperationException("No se pudo crear el Job Object.");
        var limits = new Native.JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
        limits.BasicLimitInformation.LimitFlags = Native.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        int size = Marshal.SizeOf(typeof(Native.JOBOBJECT_EXTENDED_LIMIT_INFORMATION));
        IntPtr buffer = Marshal.AllocHGlobal(size);
        try {
            Marshal.StructureToPtr(limits, buffer, false);
            if (!Native.SetInformationJobObject(job, Native.JobObjectExtendedLimitInformation, buffer, (uint)size))
                throw new InvalidOperationException("No se pudo configurar el Job Object.");
        } finally { Marshal.FreeHGlobal(buffer); }

        string powershell = Path.Combine(Environment.SystemDirectory, "WindowsPowerShell\\v1.0\\powershell.exe");
        string script = Path.Combine(root, "scripts\\Service.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException("Falta scripts\\Service.ps1 en la instancia.");
        var info = new ProcessStartInfo(powershell,
            "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + script + "\" -Root \"" + root + "\"");
        info.UseShellExecute = false;
        info.CreateNoWindow = true;
        info.WorkingDirectory = root;
        child = new Process();
        child.StartInfo = info;
        child.EnableRaisingEvents = true;
        child.Exited += ChildExited;
        child.Start();
        if (!Native.AssignProcessToJobObject(job, child.Handle)) {
            try { child.Kill(); } catch (Exception) { }
            throw new InvalidOperationException("No se pudo asociar el proceso al Job Object.");
        }
    }

    private void ChildExited(object sender, EventArgs e) {
        if (stopping || probing) return;
        int code = -1;
        try { code = child.ExitCode; } catch (Exception) { }
        try { EventLog.WriteEntry("SIGER termino inesperadamente (codigo " + code + "). Revisa logs\\ de la instancia.", EventLogEntryType.Error); } catch (Exception) { }
        // Salida con error sin informar SERVICE_STOPPED: el administrador de servicios aplica la recuperacion.
        Environment.Exit(1);
    }

    protected override void OnStop() { StopChild(); }
    protected override void OnShutdown() { StopChild(); }

    /** true si hubo que terminar el arbol de procesos por exceder el plazo. */
    private bool StopChild() {
        stopping = true;
        bool forced = false;
        if (!probing) RequestAdditionalTime(stopWaitMs + 15000);
        try {
            if (child != null && !child.HasExited) {
                File.WriteAllText(StopFile(), "SERVICE_STOP");
                if (!child.WaitForExit(stopWaitMs)) {
                    forced = true;
                    if (!probing) { try { EventLog.WriteEntry("SIGER no se detuvo en el plazo; se terminan sus procesos.", EventLogEntryType.Warning); } catch (Exception) { } }
                    Native.TerminateJobObject(job, 1);
                    child.WaitForExit(10000);
                }
            }
        } finally {
            if (job != IntPtr.Zero) { Native.CloseHandle(job); job = IntPtr.Zero; }
        }
        return forced;
    }

    private string StopFile() { return Path.Combine(root, "run\\stop.request"); }

    private static class Native {
        public const int JobObjectExtendedLimitInformation = 9;
        public const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;

        [StructLayout(LayoutKind.Sequential)]
        public struct JOBOBJECT_BASIC_LIMIT_INFORMATION {
            public long PerProcessUserTimeLimit;
            public long PerJobUserTimeLimit;
            public uint LimitFlags;
            public UIntPtr MinimumWorkingSetSize;
            public UIntPtr MaximumWorkingSetSize;
            public uint ActiveProcessLimit;
            public UIntPtr Affinity;
            public uint PriorityClass;
            public uint SchedulingClass;
        }
        [StructLayout(LayoutKind.Sequential)]
        public struct IO_COUNTERS {
            public ulong ReadOperationCount, WriteOperationCount, OtherOperationCount;
            public ulong ReadTransferCount, WriteTransferCount, OtherTransferCount;
        }
        [StructLayout(LayoutKind.Sequential)]
        public struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION {
            public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
            public IO_COUNTERS IoInfo;
            public UIntPtr ProcessMemoryLimit;
            public UIntPtr JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed;
            public UIntPtr PeakJobMemoryUsed;
        }
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr CreateJobObject(IntPtr attributes, string name);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool SetInformationJobObject(IntPtr job, int infoClass, IntPtr info, uint length);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool TerminateJobObject(IntPtr job, uint exitCode);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool CloseHandle(IntPtr handle);
    }
}
