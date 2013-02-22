using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace Assignment2
{
    #region results

    public interface IDo
    {
        bool CanPing { get; set; }
        bool HasDns { get; }
        string IpOrWord { get; }
    }

    public class IpResult : IDo
    {
        public IpResult(IPAddress addr)
        {
            Address = addr;
        }

        public IPAddress Address { get; private set; }
        public string HostName { get; set; }
        public bool HasDns { get { return HostName != null; } }
        public string IpOrWord { get { return Address.ToString(); } }
        public bool CanPing { get; set; }
    }

    public class DnsResult : IDo
    {
        public DnsResult(string word)
        {
            Word = word;
        }

        public string Word { get; private set; }
        public string HostName { get; set; }
        public bool HasDns { get { return HostName != null; } }
        public string IpOrWord { get { return Word; } }
        public bool CanPing { get; set; }

        public HashSet<string> PossibleDns
        {
            get
            {
                return new HashSet<string>
                    {
                        Word + ".com",
                        Word + ".edu",
                        Word + ".org"
                    };
            }
        }
    }

    public class Results
    {
        public Results()
        {
            R = new List<IDo>();
        }

        public IList<IDo> R { get; set; }

        public double PercentWithDns
        {
            get { return (double)R.Count(x => x.HasDns) / R.Count; }
        }

        public double PercentPingable
        {
            get { return (double)R.Count(x => x.CanPing) / R.Count; }
        }

        public double PercentBoth
        {
            get { return (double) R.Count(x => x.CanPing || x.HasDns)/R.Count; }
        }
    }

    #endregion

    public class Program
    {
        private static readonly Results IpResults = new Results();
        private static readonly Results DnsResults = new Results();

        public static void Main(string[] args)
        {
            var ips = GetRandomIps(20);
            var words = GetRandomWords(20);

            var threads = new List<Thread>();

            foreach (var ip in ips)
            {
                var ip1 = ip;
                var t = new Thread(() =>
                {
                    Console.WriteLine("Checking ip " + ip1);
                    IpResults.R.Add(new IpResult(ip1)
                        {
                            HostName = GetHostName(ip1),
                            CanPing = Ping(ip1)
                        });
                });
                threads.Add(t);
                t.Start();
            }

            foreach (var word in words)
            {
                var word1 = word;
                var t = new Thread(() =>
                {
                    Console.WriteLine("Checking word " + word1);
                    var r = new DnsResult(word1);
                    r.HostName = r.PossibleDns.Select(GetHostName).FirstOrDefault(x => x != null);
                    r.CanPing = r.PossibleDns.Select(Ping).FirstOrDefault(x => x);
                    DnsResults.R.Add(r);
                });
                threads.Add(t);
                t.Start();
            }

            foreach (var thread in threads.Where(thread => thread != null && thread.ThreadState != ThreadState.Stopped))
            {
                thread.Join();
            }

            Console.WriteLine("IP Address\tHasDns\tCanPing\t");
            foreach (var result in IpResults.R)
            {
                Console.WriteLine("{0} {1} {2}", result.IpOrWord, result.HasDns, result.CanPing);
            }

            Console.WriteLine("Word\tHasDns\tCanPing\t");
            foreach (var result in DnsResults.R)
            {
                Console.WriteLine("{0} {1} {2}", result.IpOrWord, result.HasDns, result.CanPing);
            }

            Console.Write("******** TOTALS **********");
            Console.WriteLine("IP % with DNS: " + IpResults.PercentWithDns);
            Console.WriteLine("IP % with Ping: " + IpResults.PercentPingable);
            Console.WriteLine("IP % with Both: " + IpResults.PercentBoth);
            Console.WriteLine("DNS % with DNS: " + DnsResults.PercentWithDns);
            Console.WriteLine("DNS % with Ping: " + DnsResults.PercentPingable);
            Console.WriteLine("DNS % with Both: " + DnsResults.PercentBoth);
            Console.ReadKey();
        }

        #region helpers

        public static string GetHostName(string name)
        {
            try
            {
                return Dns.GetHostEntry(name).HostName;
            }
            catch (SocketException)
            {
                return null;
            }
        }

        public static bool Ping(string name)
        {
            var pingSender = new Ping();

            // Use the default Ttl value which is 128,
            // but change the fragmentation behavior.
            var options = new PingOptions { DontFragment = true };

            // Create a buffer of 32 bytes of data to be transmitted.
            var data = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            var buffer = Encoding.ASCII.GetBytes(data);
            var timeout = 120;
            try
            {
                var reply = pingSender.Send(name, timeout, buffer, options);
                return reply.Status == IPStatus.Success;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        public static bool Ping(IPAddress address)
        {
            var pingSender = new Ping();

            // Use the default Ttl value which is 128,
            // but change the fragmentation behavior.
            var options = new PingOptions { DontFragment = true };

            // Create a buffer of 32 bytes of data to be transmitted.
            var data = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            var buffer = Encoding.ASCII.GetBytes(data);
            var timeout = 120;
            try
            {
                var reply = pingSender.Send(address, timeout, buffer, options);
                return reply.Status == IPStatus.Success;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        public static string GetHostName(IPAddress address)
        {
            try
            {
                return Dns.GetHostEntry(address).HostName;
            }
            catch (SocketException)
            {
                return null;
            }
        }

        private static readonly Random Rand = new Random();

        public static IList<IPAddress> GetRandomIps(int count)
        {
            return Enumerable.Range(0, count)
                             .Select(x => string.Join(".", new[]
                                 {
                                     Rand.Next(1, 218),
                                     Rand.Next(0, 255),
                                     Rand.Next(0, 255), 
                                     Rand.Next(0, 255),
                                 }))
                             .Select(IPAddress.Parse)
                             .ToList();
        }

        public static IList<string> GetRandomWords(int count)
        {
            #region words

            var words = new[]
                {
                    "amoroso",
                    "anele",
                    "associated",
                    "battleax",
                    "brotherly",
                    "chickaree",
                    "compensation",
                    "displace",
                    "herbarist",
                    "matross",
                    "misdo",
                    "nolition",
                    "regards",
                    "solus",
                    "spavined",
                    "tragalism",
                    "ululation",
                    "untrammeled",
                    "virescent",
                    "wellknown",
                    "activity",
                    "adaption",
                    "backbiter",
                    "bandbox",
                    "blowth",
                    "bouncing",
                    "capitalist",
                    "chersonese",
                    "conspicuousness",
                    "disparagement",
                    "dominos",
                    "foreloper",
                    "messmate",
                    "rioter",
                    "sentimentalism",
                    "shallowbrain",
                    "spicilegium",
                    "tiresias",
                    "toboggan",
                    "undoubted",
                };

            #endregion
            
            var randWords = new HashSet<string>();

            while (randWords.Count < count)
            {
                var idx = Rand.Next(0, words.Length - 1);
                randWords.Add(words[idx]);
            }

            return randWords.ToList();
        }

        #endregion
    }
}
