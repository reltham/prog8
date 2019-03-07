%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    ; @todo when using -noopt, the error "can only push address of string that is a variable on the heap" appears when using strings



    sub start() {

        uword w = 12345
        ubyte flags

        c64.CHROUT('\n')

        clear_irqd()
        c64utils.uword2bcd(w)
        flags=read_flags()
        c64scr.print_ubbin(1,flags)
        c64.CHROUT('\n')

        set_irqd()
        c64utils.uword2bcd(w)
        flags=read_flags()
        c64scr.print_ubbin(1,flags)
        c64.CHROUT('\n')

        clear_irqd()
        c64utils.uword2bcd(w)
        flags=read_flags()
        c64scr.print_ubbin(1,flags)
        c64.CHROUT('\n')

        set_irqd()
        c64utils.uword2bcd(w)
        flags=read_flags()
        c64scr.print_ubbin(1,flags)
        c64.CHROUT('\n')

    }
}
