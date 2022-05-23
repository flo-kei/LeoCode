import {AfterViewInit, Component, Input, OnInit, ViewChild} from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import {MatTable, MatTableDataSource} from '@angular/material/table';
import { ListExampleDataSource, ListExampleItem } from './list-example-datasource';
import {Example} from '../model/example.model';
import {HttpService} from '../services/http.service';
import {ActivatedRoute, Router} from '@angular/router';

@Component({
  selector: 'app-list-example',
  templateUrl: './list-example.component.html',
  styleUrls: ['./list-example.component.css']
})
export class ListExampleComponent implements AfterViewInit, OnInit {
  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;
  @ViewChild(MatTable) table: MatTable<Example>;
  dataSource: MatTableDataSource<Example>;
  value: number;

  /** Columns displayed in the table. Columns IDs can be added, removed, or reordered. */
  displayedColumns = ['name', 'description', 'type'];

  constructor(private http: HttpService,
              public router: Router,
              private route: ActivatedRoute) {
    this.value = Number(this.route.snapshot.paramMap.get('value'));
  }

  ngOnInit(): void {
    this.dataSource = new MatTableDataSource<Example>();
    this.refreshData();
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
    this.table.dataSource = this.dataSource;
  }

  refreshData(): void {
    this.http.getExampleList().subscribe(exampleList => this.dataSource.data = exampleList, error => console.log(error));
  }
}
