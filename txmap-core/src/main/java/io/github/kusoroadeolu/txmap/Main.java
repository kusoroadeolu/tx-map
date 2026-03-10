package io.github.kusoroadeolu.txmap;

public class Main {
    void main(){
        Node node = new Node();
        node.next = node;
        IO.println(node == node.next);
    }


    static class Node{
        Node next;
    }
}
