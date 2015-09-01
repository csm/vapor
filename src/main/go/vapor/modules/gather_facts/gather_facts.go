package gather_facts

import (
	"os"
	"github.com/bjeanes/go-edn"
	"github.com/bjeanes/go-edn/types"
	"net"
	"fmt"
)

func main() {
	var facts edn.types.Map
	var ifaces =
	fmt.Print(edn.DumpString(facts))
}