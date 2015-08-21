# vapor design notes

## Automation

We've used Ansible and we like it a lot; the agentless design is very cool for deploying in existing setups, where you might only have SSH available. The flexibility of the inventory system, the module system, et al are all awesome.

But, can we do something better?

Go (the language) is also awesome; compile, statically link, and run. No dependencies on libc, no shared libraries, which can lead to no end of pain unless your environment is completely homogeneous.

Clojure is awesome too, which is why we are writing things in it.

What if instead of Python modules in Ansible (or really whatever you want, but it's up to you to make sure something can run remotely) we uploaded small, static native programs to the host, ran them, and grabbed stdout as the answer? What if we could work in our native tongue (Clojure) to develop our module scripts, and run that, without any additional dependencies, on the target?

Call it µ-Clojure for now. Basic idea would be to convert µclj code to LLVM bitcode, then compile and link that for the target host. We'd take a hint from Go and eschew libc, making direct system calls when we wanted to do IO. Transport to the host could use SSH, but might use something else for other special cases. Instead of spitting out JSON, it can spit out edn (and sure, if you want to run Python or Ruby or whatever, you could do so, just make sure you spit out edn).

### Challenges

* Fact-gathering. Imagine a remote host, that you have no idea what OS it runs or even what architecture it is. Maybe bootstrapping fact-gathering with shell scripts would work, or just `uname`.

* Maybe we can even do better than edn?